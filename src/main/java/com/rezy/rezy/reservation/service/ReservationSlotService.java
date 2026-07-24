package com.rezy.rezy.reservation.service;

import com.rezy.rezy.reservation.repository.ReservationSlotRepository;
import com.rezy.rezy.reservation.domain.ReservationSlot;
import com.rezy.rezy.reservation.dto.PartySizeCapacityRequest;
import com.rezy.rezy.reservation.dto.ScheduleCreateRequest;
import com.rezy.rezy.reservation.dto.ScheduleCreateResponse;
import com.rezy.rezy.store.StoreRepository;
import com.rezy.rezy.store.domain.Store;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class ReservationSlotService {

    private final StoreRepository storeRepository;
    private final ReservationSlotRepository reservationSlotRepository;

    // 예약 스케줄 생성 - 내 가게를 찾아 예약 스케줄 이미 있는지 체크 후
    // 기간 전체 슬롯 만들어서 저장 + 생성 요약을 반환
    @Transactional
    public ScheduleCreateResponse createSchedule(String managerId, ScheduleCreateRequest request) {

        // 1) 로그인한 사장님 가게 조회 (없으면 가게 등록부터 해야된다는 안내)
        Store store = storeRepository.findByManagerUserId(managerId)
                .orElseThrow(() -> new IllegalStateException("가게를 먼저 등록해주세요"));

        // 2) 값 검증 (기간, 시간, 인원 규칙)
        validate(request);

        // 3) 해당 기간에 이미 슬롯 있으면 거절
        LocalDateTime rangeStart = request.getStartDate().atStartOfDay();
        LocalDateTime rangeEnd = request.getEndDate().atTime(LocalTime.MAX);

        if(reservationSlotRepository.existsByStore(store)) {
            throw new IllegalStateException("이미 예약 스케줄을 생성했습니다. 수정만 가능합니다.");
        }

        // 4) 슬롯 대량 생성
        List<ReservationSlot> slots = generateSlots(store, request);

        // 5) 저장
        reservationSlotRepository.saveAll(slots);

        return ScheduleCreateResponse.of(slots.size(), request.getStartDate(), request.getEndDate());
    }

    // 유효성 검사
    private void validate(ScheduleCreateRequest request) {

        if(request.getEndDate().isBefore(request.getStartDate())) {
            throw new IllegalArgumentException("종료일이 시작일보다 빠를 수 없습니다.");
        }
        if (request.getEndDate().isAfter(request.getStartDate().plusMonths(1))) {
            throw new IllegalArgumentException("예약 기간은 최대 한 달까지 설정할 수 있습니다.");
        }
        if (!request.getOpenTime().isBefore(request.getCloseTime())) {
            throw new IllegalArgumentException("오픈 시간이 마감 시간보다 빨라야 합니다.");
        }
        long distinctSizes = request.getCapacities().stream()
                .map(PartySizeCapacityRequest::getPartySize).distinct().count();
        if (distinctSizes != request.getCapacities().size()) {
            throw new IllegalArgumentException("같은 인원수가 중복으로 지정되었습니다.");
        }
    }

    // 슬롯 생성
    private List<ReservationSlot> generateSlots(Store store, ScheduleCreateRequest request) {

        List<ReservationSlot> slots = new ArrayList<>();
        Set<DayOfWeek> targetDays = new HashSet<>(request.getDaysOfWeek());

        int openMinute = request.getOpenTime().toSecondOfDay() / 60;
        int closeMinute = request.getCloseTime().toSecondOfDay() / 60;

        for (LocalDate date = request.getStartDate(); !date.isAfter(request.getEndDate()); date = date.plusDays(1)) {

            // 지정한 요일 아니면 pass
            if (!targetDays.contains(date.getDayOfWeek())) continue;

            //
            for (int m = openMinute; m < closeMinute; m += request.getIntervalMinutes()) {
                LocalDateTime slotDateTime = LocalDateTime.of(date, LocalTime.ofSecondOfDay(m * 60L));

                ReservationSlot slot = ReservationSlot.create(store, slotDateTime);

                for (PartySizeCapacityRequest cap : request.getCapacities()) {
                    slot.addCapacity(cap.getPartySize(), cap.getTeamCount());
                }

                slots.add(slot);
            }
        }

        return slots;
    }
}