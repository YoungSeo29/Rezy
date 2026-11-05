package com.rezy.rezy.reservation.service;

import com.rezy.rezy.global.exception.NotFoundException;
import com.rezy.rezy.global.redis.RedisKeys;
import com.rezy.rezy.reservation.domain.Reservation;
import com.rezy.rezy.reservation.domain.ReservationSlot;
import com.rezy.rezy.reservation.domain.ReservationStatus;
import com.rezy.rezy.reservation.domain.SlotCapacity;
import com.rezy.rezy.reservation.domain.WaitingEntry;
import com.rezy.rezy.reservation.domain.WaitingStatus;
import com.rezy.rezy.reservation.dto.MyReservationResponse;
import com.rezy.rezy.reservation.dto.ReservationCreateRequest;
import com.rezy.rezy.reservation.dto.ReservationListType;
import com.rezy.rezy.reservation.dto.ReservationResponse;
import com.rezy.rezy.reservation.dto.ReserveResponse;
import com.rezy.rezy.reservation.dto.WaitingResponse;
import com.rezy.rezy.reservation.repository.ReservationRepository;
import com.rezy.rezy.reservation.repository.SlotCapacityRepository;
import com.rezy.rezy.reservation.repository.WaitingEntryRepository;
import com.rezy.rezy.user.UserRepository;
import com.rezy.rezy.user.domain.User;
import com.rezy.rezy.user.domain.UserRole;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

// 실제 예약 생성용
@Slf4j
@Service
@RequiredArgsConstructor
public class ReservationService {

    private final UserRepository userRepository;
    private final SlotCapacityRepository slotCapacityRepository;
    private final ReservationRepository reservationRepository;
    private final WaitingEntryRepository waitingEntryRepository;
    private final WaitingService waitingService;

    // Redis 접근용 - 문자열 전용이라서 DECR/INCR 사용 가능
    private final StringRedisTemplate redisTemplate;

    // 예약 생성 - User 검증 -> 하루 1건인지 검증-> Redis 재고 차감 -> 예약 저장
    // 기존과 달리 slot_capacities 행에 lock 안걺. 초과 예약은 Redis가 막아줌
    @Transactional
    public ReserveResponse reserve(String userId, ReservationCreateRequest request) {

        String capacityId = request.getSlotCapacityId();

        // 1)  예약자 조회 + USER 권한인지 확인
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new NotFoundException("사용자를 찾을 수 없습니다."));

        if(user.getRole() != UserRole.USER) {
            throw new IllegalStateException("예약은 일반 사용자만 가능합니다.");
        }

        // 2-1) 날짜만 조회 - "하루 1건"검증에 쓸 날짜만 가져오기
        LocalDateTime slotDatetime = slotCapacityRepository
                .findSlotDatetimeById(capacityId)
                .orElseThrow(() -> new NotFoundException("존재하지 않는 예약 옵션입니다."));

        // 2-2) 임박 슬롯(1시간 전)은 예약도 줄서기도 안 받음
        if (ReservationSlot.isBookingClosed(slotDatetime, LocalDateTime.now())) {
            throw new IllegalStateException("시작 1시간 전부터는 예약할 수 없습니다.");
        }

        LocalDate date = slotDatetime.toLocalDate();

        // 2-3) 하루 1건 제한 확인
        boolean alreadyBooked = reservationRepository.existsActiveOnDate( userId, date.atStartOfDay(), date.plusDays(1).atStartOfDay(), ReservationStatus.CANCELLED);
        if(alreadyBooked) {
            throw new IllegalStateException("같은 날짜에는 하루에 한 건만 예약할 수 있습니다.");
        }

        // 3) Redis 에 저장 할 key 완성
        String stockKey = RedisKeys.stock(capacityId);

        // 3-1) 키 없으면 DB에 가서 초기값 가져오기 - 최초 1회만 동작 - 검증은 loadStockIfAbsent() 내에서
        loadStockIfAbsent(stockKey, capacityId);

        // 3-2) 원자적 차감
        // Redis는 명령을 직렬로 처리. 500개 요청 동시에 들어와도 순서대로 깎임.
        // 반환 값은, "-1 된 후의 잔여 수량"
        Long remaining = redisTemplate.opsForValue().decrement(stockKey);

        // remainingTeams가 음수면 원상복귀 시키고 줄을 세운다.
        // DECR은 음수까지 내려가기 때문에 check 필요..
        // 자리를 얻는 길은 DECR 성공뿐이라, 만석일 때 새로 온 사람은 줄 선 사람을 가로챌 수 없다
        if(remaining == null || remaining < 0) {
            redisTemplate.opsForValue().increment(stockKey);
            return ReserveResponse.waiting(enqueue(user, capacityId));
        }

        // "트랜잭션 끝나면 롤백 여부 확인해서, 롤백이면 INCR 해라" 라고 예약 걸어둠
        // 기존 try/catch 는 커밋 시점의 실패를 놓쳤다
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCompletion(int status) {
                if (status == STATUS_ROLLED_BACK) {
                    redisTemplate.opsForValue().increment(stockKey);
                }
            }
        });

        // 4) 예약 저장
        // remainingTeams 는 갱신하지 않음
        SlotCapacity capacity = slotCapacityRepository.findById(capacityId)
                .orElseThrow(() -> new NotFoundException("존재하지 않는 예약 옵션입니다."));

        Reservation reservation = Reservation.create(user, capacity);
        reservationRepository.save(reservation);

        return ReserveResponse.reserved(ReservationResponse.from(reservation));
    }

    // 만석 - 번호표 발급
    private WaitingResponse enqueue(User user, String capacityId) {

        if (waitingEntryRepository.existsInLine(user.getUserId(), capacityId,
                List.of(WaitingStatus.WAITING, WaitingStatus.OFFERED))) {
            throw new IllegalStateException("이미 대기 중인 예약 옵션입니다.");
        }

        SlotCapacity capacity = slotCapacityRepository.findById(capacityId)
                .orElseThrow(() -> new NotFoundException("존재하지 않는 예약 옵션입니다."));

        WaitingEntry entry = WaitingEntry.create(user, capacity);
        waitingEntryRepository.save(entry);   // createdAt·waitingId 확정

        // 줄 설 때와 조회할 때 번호 기준이 어긋나지 않도록 getMyWaitings 와 같은 집계를 쓴다
        // (OFFERED 도 내 앞으로 셈, 자기 자신은 waitingId 비교로 빠진다)
        long ahead = waitingEntryRepository.countAhead(
                capacityId, entry.getCreatedAt(), entry.getWaitingId());

        return WaitingResponse.of(entry, ahead + 1);
    }

    // 예약 취소
    // 본인 확인 -> 상태변경 -> 비워진 자리를 대기 1번에게, 없으면 커밋 성공후 Redis 재고 복구
    @Transactional
    public void cancelReservation(String userId, String reservationId) {

        // 잠그지 않으면 동시 취소 둘 다 CONFIRMED 를 보고 자리를 두 번 푼다
        Reservation reservation = reservationRepository.findByIdForUpdate(reservationId)
                .orElseThrow(() -> new NotFoundException("존재하지 않는 예약입니다."));

        // [본인 확인] 본인 예약만 취소 가능
        if (!reservation.getUser().getUserId().equals(userId)) {
            throw new IllegalStateException("본인의 예약만 취소할 수 있습니다.");
        }

        // 상태 CANCELLED로 변경
        reservation.cancel(LocalDateTime.now());

        // 대기 줄이 있으면 1번에게 직접 넘기고 INCR 안 함, 줄이 비었으면 커밋 후 INCR
        waitingService.releaseSeat(reservation.getCapacity().getSlotCapacityId());
    }

    // Redis에 재고 키가 없을 때 최초 1회 DB에서 계산해 올림.
    // remaining_teams는 예약 시 갱신X → 읽지 않는다
    // 총정원 - CONFIRMED 예약수 - OFFERED 대기수 로 계산.
    // OFFERED 를 안 빼면 키 소실 후 대기 1번에게 넘긴 자리가 공용 재고로 풀려 오버부킹이 난다
    private void loadStockIfAbsent(String stockKey, String capacityId) {

        // 이미 재고 key  있으면 return
        if(Boolean.TRUE.equals(redisTemplate.hasKey(stockKey))) {
            return;
        }

        // 총정원 (처음에 사장님이 설정한)
        int totalTeams = slotCapacityRepository.findTotalTeamsById(capacityId)
                .orElseThrow(() -> new NotFoundException("존재하지 않는 예약 옵션입니다."));

        long confirmed = reservationRepository.countByCapacityAndStatus(capacityId, ReservationStatus.CONFIRMED);
        long offered = waitingEntryRepository.countByCapacityAndStatus(capacityId, WaitingStatus.OFFERED);

        int remaining = totalTeams - (int) (confirmed + offered);

        if(remaining < 0) {
            log.error("[재고] 잡힌 자리가 총정원을 초과함. capacityId={}, total={}, confirmed={}, offered={}",
                    capacityId, totalTeams, confirmed, offered);
            remaining = 0;
        }

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
