package com.rezy.rezy.reservation.service;

import com.rezy.rezy.global.redis.RedisKeys;
import com.rezy.rezy.reservation.domain.Reservation;
import com.rezy.rezy.reservation.domain.ReservationSlot;
import com.rezy.rezy.reservation.domain.ReservationStatus;
import com.rezy.rezy.reservation.domain.SlotCapacity;
import com.rezy.rezy.reservation.dto.MyReservationResponse;
import com.rezy.rezy.reservation.dto.ReservationCreateRequest;
import com.rezy.rezy.reservation.dto.ReservationListType;
import com.rezy.rezy.reservation.dto.ReservationResponse;
import com.rezy.rezy.reservation.repository.ReservationRepository;
import com.rezy.rezy.reservation.repository.SlotCapacityRepository;
import com.rezy.rezy.user.UserRepository;
import com.rezy.rezy.user.domain.User;
import com.rezy.rezy.user.domain.UserRole;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

// 실제 예약 생성용
@Service
@RequiredArgsConstructor
public class ReservationService {

    private final UserRepository userRepository;
    private final SlotCapacityRepository slotCapacityRepository;
    private final ReservationRepository reservationRepository;

    // Redis 접근용 - 문자열 전용이라서 DECR/INCR 사용 가능
    private final StringRedisTemplate redisTemplate;

    // 예약 생성 - User 검증 -> 하루 1건인지 검증-> Redis 재고 차감 -> 예약 저장
    // 기존과 달리 slot_capacities 행에 lock 안걺. 초과 예약은 Redis가 막아줌
    @Transactional
    public ReservationResponse reserve(String userId, ReservationCreateRequest request) {

        String capacityId = request.getSlotCapacityId();

        // 1)  예약자 조회 + USER 권한인지 확인
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("사용자를 찾을 수 없습니다."));

        if(user.getRole() != UserRole.USER) {
            throw new IllegalStateException("예약은 일반 사용자만 가능합니다.");
        }

        // 2-1) 날짜만 조회 - "하루 1건"검증에 쓸 날짜만 가져오기
        LocalDateTime slotDatetime = slotCapacityRepository
                .findSlotDatetimeById(capacityId)
                .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 예약 옵션입니다."));

        LocalDate date = slotDatetime.toLocalDate();

        // 2-2) 하루 1건 제한 확인
        boolean alreadyBooked = reservationRepository.existsActiveOnDate( userId, date.atStartOfDay(), date.plusDays(1).atStartOfDay(), ReservationStatus.CANCELLED);
        if(alreadyBooked) {
            throw new IllegalStateException("같은 날짜에는 하루에 한 건만 예약할 수 있습니다.");
        }

        // @@ 비관적 lock 코드
        // 3) 예약할 버킷 확인
        // 락 획득 - 이 시점부터 Trx 종료까지. 같은 버킷 노리는 다른 요청은 대기
        SlotCapacity capacity = slotCapacityRepository.findByIdForUpdate(request.getSlotCapacityId())
                .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 예약 옵션입니다."));

        ReservationSlot slot = capacity.getSlot();

        // 4) 잔여 차감 (remaining_teams 확인은 이 함수 내부에서)
        capacity.decreaseRemaining();

        // 5) 예약 저장
        Reservation reservation = Reservation.create(user, slot.getStore(), slot, capacity.getPartySize());

        reservationRepository.save(reservation);

        return ReservationResponse.from(reservation);


        /* Redis 코드
        // 3) Redis 에 저장 할 key 완성
        String stockKey = RedisKeys.stock(capacityId);

        // 3-1) 키 없으면 DB에 가서 초기값 가져오기 - 최초 1회만 동작 - 검증은 loadStockIfAbsent() 내에서
        loadStockIfAbsent(stockKey, capacityId);

        // 3-2) 원자적 차감
        // Redis는 명령을 직렬로 처리. 500개 요청 동시에 들어와도 순서대로 깎임.
        // 반환 값은, "-1 된 후의 잔여 수량"
        Long remaining = redisTemplate.opsForValue().decrement(stockKey);

        // remainingTeams가 음수면 원상복귀 시키고 거절.
        // DECR은 음수까지 내려가기 때문에 check 필요..
        if(remaining == null || remaining < 0) {
            redisTemplate.opsForValue().increment(stockKey);
            throw new IllegalStateException("잔여 좌석이 없습니다.");
        }

        // 4) 예약 저장
        // remainingTeams는 갱신X
        // update하는 순간 그 행에 X-lock 걸려서 Redis로 없앤 직렬화가 살아나기 때문.
        try{
            // 예약 저장에 필요한 capacity 엔티티 조회 (slot, store 정보용)
            SlotCapacity capacity = slotCapacityRepository.findById(capacityId)
                    .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 예약 옵션입니다."));

            ReservationSlot slot = capacity.getSlot();

            // 예약 entity 생성 (상태 = confirmed)
            Reservation reservation = Reservation.create(user, slot.getStore(), slot, capacity.getPartySize());

            // DB에 새 행 insert (slot_capacities 안건들임 -> 락 경합X)
            reservationRepository.save(reservation);

            return ReservationResponse.from(reservation);

        } catch (RuntimeException e) {

            // 저장은 실패했는데 Redis 재고만 -1 되면 안되기때문에
            // -1 한거 원상복귀.
            redisTemplate.opsForValue().increment(stockKey);
            throw e;
        }
         */
    }

    // Redis에 재고 키가 없으면 DB의 remainingTeams를 최초 1회 갖고옴.
    private void loadStockIfAbsent(String stockKey, String capacityId) {

        // 이미 재고 key  있으면 return
        if(Boolean.TRUE.equals(redisTemplate.hasKey(stockKey))) {
            return;
        }

        // 재고 key 없으면
        // DB에서 최초 잔여 수량 조회
        Integer remaining = slotCapacityRepository.findRemainingTeamsById(capacityId)
                .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 예약 옵션입니다."));

        // setIfAbsent를 쓴 이유? 원자적으로 키가 없을 때만 쓴다 - Race Condition 예방 (동시 요청이어도 한 번만 세팅됨)
        redisTemplate.opsForValue().setIfAbsent(stockKey, String.valueOf(remaining));
    }

    @Transactional (readOnly = true)
    public List<MyReservationResponse> getMyReservations(String userId, ReservationListType type) {

        LocalDateTime now = LocalDateTime.now();

        List<Reservation> reservations = (type == ReservationListType.UPCOMING)
                ? reservationRepository.findUpcoming(userId, now, ReservationStatus.CONFIRMED)
                : reservationRepository.findPast(userId, now, ReservationStatus.CONFIRMED);

        return reservations.stream()
                .map(MyReservationResponse::from)
                .toList();
    }
}
