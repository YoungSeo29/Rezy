package com.rezy.rezy.reservation.service;

import com.rezy.rezy.reservation.domain.Reservation;
import com.rezy.rezy.reservation.domain.ReservationSlot;
import com.rezy.rezy.reservation.domain.ReservationStatus;
import com.rezy.rezy.reservation.domain.SlotCapacity;
import com.rezy.rezy.reservation.dto.ReservationCreateRequest;
import com.rezy.rezy.reservation.dto.ReservationResponse;
import com.rezy.rezy.reservation.repository.ReservationRepository;
import com.rezy.rezy.reservation.repository.SlotCapacityRepository;
import com.rezy.rezy.user.UserRepository;
import com.rezy.rezy.user.domain.User;
import com.rezy.rezy.user.domain.UserRole;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDate;

@Service
@RequiredArgsConstructor
public class ReservationService {

    private final UserRepository userRepository;
    private final SlotCapacityRepository slotCapacityRepository;
    private final ReservationRepository reservationRepository;

    // 예약 생성 - User 검증 -> 하루 1건인지 검증-> 잔여 차감 -> 예약 저장 후 응답 반환
    @Transactional
    public ReservationResponse reserve(String userId, ReservationCreateRequest request) {

        // 1)  예약자 조회 + USER 권한인지 확인
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("사용자를 찾을 수 없습니다."));

        if(user.getRole() != UserRole.USER) {
            throw new IllegalStateException("예약은 일반 사용자만 가능합니다.");
        }

        // 2) 예약할 튜플 확인
        SlotCapacity capacity = slotCapacityRepository.findById(request.getSlotCapacityId())
                .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 예약 옵션입니다."));
        ReservationSlot slot = capacity.getSlot();
        LocalDate date = slot.getSlotDatetime().toLocalDate();

        // 3) 하루 1건 제한 확인
        boolean alreadyBooked = reservationRepository.existsActiveOnDate( userId, date.atStartOfDay(), date.plusDays(1).atStartOfDay(), ReservationStatus.CANCELLED);
        if(alreadyBooked) {
            throw new IllegalStateException("같은 날짜에는 하루에 한 건만 예약할 수 있습니다.");
        }

        // 4) 잔여 차감
        capacity.decreaseRemaining();

        // 5) 예약 저장
        Reservation reservation = Reservation.create(user, slot.getStore(), slot, capacity.getPartySize());

        reservationRepository.save(reservation);

        return ReservationResponse.from(reservation);
    }
}
