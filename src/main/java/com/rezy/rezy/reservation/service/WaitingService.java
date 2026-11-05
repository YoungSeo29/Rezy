package com.rezy.rezy.reservation.service;

import com.rezy.rezy.global.exception.NotFoundException;
import com.rezy.rezy.global.redis.RedisKeys;
import com.rezy.rezy.reservation.domain.Reservation;
import com.rezy.rezy.reservation.domain.ReservationSlot;
import com.rezy.rezy.reservation.domain.ReservationStatus;
import com.rezy.rezy.reservation.domain.SlotCapacity;
import com.rezy.rezy.reservation.domain.WaitingEntry;
import com.rezy.rezy.reservation.domain.WaitingStatus;
import com.rezy.rezy.reservation.dto.MyWaitingResponse;
import com.rezy.rezy.reservation.dto.ReservationResponse;
import com.rezy.rezy.reservation.repository.ReservationRepository;
import com.rezy.rezy.reservation.repository.SlotCapacityRepository;
import com.rezy.rezy.reservation.repository.WaitingEntryRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

// 취소 대기 줄 - 비워진 자리 넘기기 / 만료 / 확정
// 줄을 건드리는 트랜잭션은 전부 버킷(slot_capacities) 행부터 잠근다
// 순서가 다르면 확정(대기 행 → 버킷)과 만료(버킷 → 대기 행)가 서로를 기다리는 데드락이 난다
// 예약 생성 핫패스는 여기를 안 거친다 (락 없이 Redis)
@Service
@RequiredArgsConstructor
public class WaitingService {

    private final WaitingEntryRepository waitingEntryRepository;
    private final SlotCapacityRepository slotCapacityRepository;
    private final ReservationRepository reservationRepository;
    private final StringRedisTemplate redisTemplate;

    // 내 대기 목록 - WAITING 은 지금 몇 번째인지, OFFERED 는 내 차례(번호 0)
    // 번호는 줄 선 시점 고정값이 아니라 조회 시점에 다시 센다 (앞사람이 빠지면 당겨진다)
    @Transactional(readOnly = true)
    public List<MyWaitingResponse> getMyWaitings(String userId) {
        return waitingEntryRepository
                .findActiveByUser(userId, List.of(WaitingStatus.WAITING, WaitingStatus.OFFERED))
                .stream()
                .map(entry -> entry.getStatus() == WaitingStatus.OFFERED
                        ? MyWaitingResponse.offered(entry)
                        : MyWaitingResponse.waiting(entry, aheadOf(entry) + 1))
                .toList();
    }

    // 내 앞에 몇명있는지 세주는 함수
    private long aheadOf(WaitingEntry entry) {
        return waitingEntryRepository.countAhead(
                entry.getCapacity().getSlotCapacityId(), entry.getCreatedAt(), entry.getWaitingId());
    }

    // 취소로 비워진 자리 1개를 넘긴다 - 취소 트랜잭션 안에서 불린다
    @Transactional(propagation = Propagation.MANDATORY)
    public void releaseSeat(String capacityId) {
        handOver(lockCapacity(capacityId));
    }

    // 확정 기회 만료 → 다음 번호로. 배치가 건마다 따로 부른다 (커넥션을 길게 안 쥐려고)
    @Transactional
    public boolean expireOffer(String waitingId) {

        String capacityId = waitingEntryRepository.findCapacityIdById(waitingId)
                .orElseThrow(() -> new NotFoundException("존재하지 않는 대기입니다."));

        SlotCapacity capacity = lockCapacity(capacityId);

        // 조건부 갱신 - 배치가 목록을 읽은 뒤 확정이 끼어들었으면 0건
        if (waitingEntryRepository.expireOffer(waitingId, LocalDateTime.now()) == 0) {
            return false;
        }

        handOver(capacity);
        return true;
    }

    // 마감된 슬롯의 대기는 더 이상 자리를 받을 수 없으므로 한 번에 끝낸다
    // OFFERED 는 건드리지 않는다 - 기한이 지나면 expireOffer 가 정리한다
    @Transactional
    public int closeEndedLines(LocalDateTime now) {
        return waitingEntryRepository.expireWaitingOfSlotsBefore(now.plus(ReservationSlot.BOOKING_CUTOFF));
    }

    // OFFERED 상태에서 확정 - 넘겨받은 자리를 소비하므로 Redis 재고는 건드리지 않는다
    @Transactional
    public ReservationResponse confirm(String userId, String waitingId) {

        WaitingEntry entry = waitingEntryRepository.findById(waitingId)
                .orElseThrow(() -> new NotFoundException("존재하지 않는 대기입니다."));

        if (!entry.getUser().getUserId().equals(userId)) {
            throw new IllegalStateException("본인의 대기만 확정할 수 있습니다.");
        }

        SlotCapacity capacity = lockCapacity(entry.getCapacity().getSlotCapacityId());

        if (ReservationSlot.isBookingClosed(capacity.getSlot().getSlotDatetime(), LocalDateTime.now())) {
            throw new IllegalStateException("예약이 마감된 시간대입니다.");
        }

        // 하루 1건 - 걸리면 아무것도 안 바꾸고 끝낸다. 최종 방어는 유니크 제약
        LocalDate date = capacity.getSlot().getSlotDatetime().toLocalDate();
        if (reservationRepository.existsActiveOnDate(userId, date.atStartOfDay(),
                date.plusDays(1).atStartOfDay(), ReservationStatus.CANCELLED)) {
            throw new IllegalStateException("같은 날짜에는 하루에 한 건만 예약할 수 있습니다.");
        }

        if (waitingEntryRepository.confirmOffer(waitingId, LocalDateTime.now()) == 0) {
            throw new IllegalStateException("만료되었습니다.");
        }

        Reservation reservation = Reservation.create(entry.getUser(), capacity);
        reservationRepository.save(reservation);

        return ReservationResponse.from(reservation);
    }

    // 동시 취소 2건이 같은 1번을 깨우지 않도록, 줄을 보는 동안만 버킷 행을 잠근다
    private SlotCapacity lockCapacity(String capacityId) {
        return slotCapacityRepository.findByIdForUpdate(capacityId)
                .orElseThrow(() -> new NotFoundException("존재하지 않는 예약 옵션입니다."));
    }

    // 대기 1번이 있으면 자리를 직접 넘기고(재고 0 유지 → 새로 온 사람이 못 가로챔), 줄이 비었으면 공용 재고로
    // 마감된 슬롯도 공용 재고로
    private void handOver(SlotCapacity capacity) {

        String capacityId = capacity.getSlotCapacityId();
        LocalDateTime now = LocalDateTime.now();

        if (!ReservationSlot.isBookingClosed(capacity.getSlot().getSlotDatetime(), now)) {
            Optional<WaitingEntry> head = waitingEntryRepository
                    .findLineForUpdate(capacityId, WaitingStatus.WAITING, PageRequest.of(0, 1))
                    .stream().findFirst();

            if (head.isPresent()) {
                head.get().offer(now);
                return;
            }
        }

        String stockKey = RedisKeys.stock(capacityId);

        // 재고 복구는 DB 커밋이 "성공한 뒤"에만 한다
        // 커밋 전에 INCR 하면 → 커밋이 실패했을 때 예약은 살아있는데 재고만 늘어서 초과 예약이 생긴다
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                // 키가 없다 = Redis 가 아직 이 버킷 재고를 관리하지 않는 상태 → 건드리지 않는다
                // (없는 키에 INCR 하면 "1" 이 새로 생겨서 재고가 틀어짐)
                if (Boolean.TRUE.equals(redisTemplate.hasKey(stockKey))) {
                    redisTemplate.opsForValue().increment(stockKey);
                }
            }
        });
    }
}
