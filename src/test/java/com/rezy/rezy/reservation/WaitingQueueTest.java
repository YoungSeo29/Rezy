package com.rezy.rezy.reservation;

import com.rezy.rezy.global.redis.RedisKeys;
import com.rezy.rezy.reservation.batch.StockReconciliationBatch;
import com.rezy.rezy.reservation.batch.WaitingQueueScheduler;
import com.rezy.rezy.reservation.domain.ReservationStatus;
import com.rezy.rezy.reservation.domain.WaitingEntry;
import com.rezy.rezy.reservation.domain.WaitingStatus;
import com.rezy.rezy.reservation.dto.MyWaitingResponse;
import com.rezy.rezy.reservation.dto.ReserveResponse;
import com.rezy.rezy.reservation.service.ReservationService;
import com.rezy.rezy.reservation.service.WaitingService;
import com.rezy.rezy.store.domain.Store;
import com.rezy.rezy.support.IntegrationTestSupport;
import com.rezy.rezy.user.domain.User;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.*;

// 취소 대기 줄 - 실제 DB + Redis 로 자리 넘기기/만료/확정 검증
class WaitingQueueTest extends IntegrationTestSupport {

    @Autowired ReservationService reservationService;
    @Autowired WaitingService waitingService;
    @Autowired WaitingQueueScheduler scheduler;
    @Autowired StockReconciliationBatch reconciliationBatch;

    @Test
    @DisplayName("만석이면 거절하지 않고 온 순서대로 번호표를 준다")
    void fullBucket_enqueuesInOrder() {
        String capacityId = saveSlot(saveStore(), tomorrowAt(19), 1);
        List<User> users = saveUsers(3);

        ReserveResponse first  = reserve(users.get(0), capacityId);
        ReserveResponse second = reserve(users.get(1), capacityId);
        ReserveResponse third  = reserve(users.get(2), capacityId);

        assertThat(first.getResult()).isEqualTo("RESERVED");
        assertThat(second.getResult()).isEqualTo("WAITING");
        assertThat(second.getWaiting().getWaitingNumber()).isEqualTo(1);
        assertThat(third.getWaiting().getWaitingNumber()).isEqualTo(2);
        assertThat(stock(capacityId)).isEqualTo("0");   // 줄 선 사람은 재고를 깎은 채로 남기지 않는다
    }

    @Test
    @DisplayName("취소하면 INCR 하지 않고 대기 1번에게 자리를 넘긴다 (5분 기한)")
    void cancel_offersToFirst() {
        String capacityId = saveSlot(saveStore(), tomorrowAt(19), 1);
        List<User> users = saveUsers(3);
        String reservationId = reserve(users.get(0), capacityId).getReservation().getReservationId();
        String firstWaiting  = reserve(users.get(1), capacityId).getWaiting().getWaitingId();
        String secondWaiting = reserve(users.get(2), capacityId).getWaiting().getWaitingId();

        reservationService.cancelReservation(users.get(0).getUserId(), reservationId);

        WaitingEntry offered = waiting(firstWaiting);
        assertThat(offered.getStatus()).isEqualTo(WaitingStatus.OFFERED);
        assertThat(offered.getOfferExpiresAt())
                .isBetween(LocalDateTime.now().plusMinutes(4), LocalDateTime.now().plusMinutes(5));
        assertThat(waiting(secondWaiting).getStatus()).isEqualTo(WaitingStatus.WAITING);

        // 공용 재고로 돌려주면 새로 온 사람이 DECR 로 가로챌 수 있다
        assertThat(stock(capacityId)).isEqualTo("0");
    }

    @Test
    @DisplayName("1번에게 자리가 넘어간 사이 새로 온 사람은 자리를 못 먹고 줄 끝에 붙는다")
    void newcomer_cannotSteal() {
        String capacityId = saveSlot(saveStore(), tomorrowAt(19), 1);
        List<User> users = saveUsers(3);
        String reservationId = reserve(users.get(0), capacityId).getReservation().getReservationId();
        reserve(users.get(1), capacityId);
        reservationService.cancelReservation(users.get(0).getUserId(), reservationId);

        ReserveResponse newcomer = reserve(users.get(2), capacityId);

        assertThat(newcomer.getResult()).isEqualTo("WAITING");
        assertThat(reservationRepository.findAll())
                .noneMatch(r -> r.getStatus() == ReservationStatus.CONFIRMED);
        assertThat(stock(capacityId)).isEqualTo("0");
    }

    @Test
    @DisplayName("확정하면 예약이 생기고 재고는 0 그대로다")
    void confirm_createsReservation() {
        String capacityId = saveSlot(saveStore(), tomorrowAt(19), 1);
        List<User> users = saveUsers(2);
        String reservationId = reserve(users.get(0), capacityId).getReservation().getReservationId();
        String waitingId = reserve(users.get(1), capacityId).getWaiting().getWaitingId();
        reservationService.cancelReservation(users.get(0).getUserId(), reservationId);

        waitingService.confirm(users.get(1).getUserId(), waitingId);

        assertThat(waiting(waitingId).getStatus()).isEqualTo(WaitingStatus.CONFIRMED);
        assertThat(reservationRepository.findAll())
                .filteredOn(r -> r.getStatus() == ReservationStatus.CONFIRMED)
                .hasSize(1);
        assertThat(stock(capacityId)).isEqualTo("0");   // 넘겨받은 자리를 소비했을 뿐
    }

    @Test
    @DisplayName("기한이 지난 뒤 확정하면 만료 예외가 나고 예약은 생기지 않는다")
    void confirm_afterExpiry_throws() {
        String capacityId = saveSlot(saveStore(), tomorrowAt(19), 1);
        List<User> users = saveUsers(2);
        String reservationId = reserve(users.get(0), capacityId).getReservation().getReservationId();
        String waitingId = reserve(users.get(1), capacityId).getWaiting().getWaitingId();
        reservationService.cancelReservation(users.get(0).getUserId(), reservationId);
        expireNow(waitingId);

        assertThatThrownBy(() -> waitingService.confirm(users.get(1).getUserId(), waitingId))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("만료되었습니다.");

        assertThat(reservationRepository.findAll())
                .noneMatch(r -> r.getStatus() == ReservationStatus.CONFIRMED);
    }

    @Test
    @DisplayName("기한이 지나면 배치가 만료시키고 2번에게 자리를 넘긴다")
    void expiry_promotesNext() {
        String capacityId = saveSlot(saveStore(), tomorrowAt(19), 1);
        List<User> users = saveUsers(3);
        String reservationId = reserve(users.get(0), capacityId).getReservation().getReservationId();
        String firstWaiting  = reserve(users.get(1), capacityId).getWaiting().getWaitingId();
        String secondWaiting = reserve(users.get(2), capacityId).getWaiting().getWaitingId();
        reservationService.cancelReservation(users.get(0).getUserId(), reservationId);
        expireNow(firstWaiting);

        scheduler.expireOffers();

        assertThat(waiting(firstWaiting).getStatus()).isEqualTo(WaitingStatus.EXPIRED);
        assertThat(waiting(secondWaiting).getStatus()).isEqualTo(WaitingStatus.OFFERED);
        assertThat(stock(capacityId)).isEqualTo("0");
    }

    @Test
    @DisplayName("만료됐는데 줄이 비었으면 그제서야 공용 재고로 돌려준다")
    void expiry_emptyLine_releasesStock() {
        String capacityId = saveSlot(saveStore(), tomorrowAt(19), 1);
        List<User> users = saveUsers(2);
        String reservationId = reserve(users.get(0), capacityId).getReservation().getReservationId();
        String waitingId = reserve(users.get(1), capacityId).getWaiting().getWaitingId();
        reservationService.cancelReservation(users.get(0).getUserId(), reservationId);
        expireNow(waitingId);

        scheduler.expireOffers();

        assertThat(waiting(waitingId).getStatus()).isEqualTo(WaitingStatus.EXPIRED);
        assertThat(stock(capacityId)).isEqualTo("1");
    }

    @Test
    @DisplayName("Redis 키가 소실되면 총정원 - CONFIRMED 로 복구하므로 만석이 유지되어 줄을 선다")
    void keyLost_confirmedOnly_staysFull() {
        String capacityId = saveSlot(saveStore(), tomorrowAt(19), 3);
        List<User> users = saveUsers(4);
        users.subList(0, 3).forEach(u -> reserve(u, capacityId));
        redisTemplate.delete(RedisKeys.stock(capacityId));   // Redis 재시작

        ReserveResponse fourth = reserve(users.get(3), capacityId);

        // remaining_teams(3) 로 복구하면 만석인 슬롯에 3팀을 더 받는다
        assertThat(fourth.getResult()).isEqualTo("WAITING");
        assertThat(stock(capacityId)).isEqualTo("0");
    }

    @Test
    @DisplayName("1번에게 넘긴 자리는 Redis 키가 소실돼도 공용 재고로 복구되지 않는다")
    void keyLost_whileOffered_staysFull() {
        String capacityId = saveSlot(saveStore(), tomorrowAt(19), 1);
        List<User> users = saveUsers(3);
        String reservationId = reserve(users.get(0), capacityId).getReservation().getReservationId();
        reserve(users.get(1), capacityId);
        reservationService.cancelReservation(users.get(0).getUserId(), reservationId);
        redisTemplate.delete(RedisKeys.stock(capacityId));

        ReserveResponse newcomer = reserve(users.get(2), capacityId);

        // CONFIRMED 만 세면 1 로 복구되어 새로 온 사람이 가져가고, 1번이 확정하면 오버부킹
        assertThat(newcomer.getResult()).isEqualTo("WAITING");
        assertThat(stock(capacityId)).isEqualTo("0");
    }

    @Test
    @DisplayName("정합성 배치는 1번에게 넘긴 자리를 불일치로 보고 공용 재고로 풀지 않는다")
    void reconciliation_respectsOffered() {
        String capacityId = saveSlot(saveStore(), tomorrowAt(19), 1);   // 배치 검사 범위(오늘~7일) 안
        List<User> users = saveUsers(2);
        String reservationId = reserve(users.get(0), capacityId).getReservation().getReservationId();
        reserve(users.get(1), capacityId);
        reservationService.cancelReservation(users.get(0).getUserId(), reservationId);

        reconciliationBatch.reconcile();
        reconciliationBatch.reconcile();   // 2주기 연속이면 보정되는 구조

        assertThat(stock(capacityId)).isEqualTo("0");
    }

    @Test
    @DisplayName("같은 버킷에서 동시에 2건이 취소되면 서로 다른 대기자 2명에게 자리가 넘어간다")
    void concurrentCancels_offerDifferentWaiters() throws InterruptedException {
        String capacityId = saveSlot(saveStore(), tomorrowAt(19), 2);
        List<User> users = saveUsers(5);
        String r1 = reserve(users.get(0), capacityId).getReservation().getReservationId();
        String r2 = reserve(users.get(1), capacityId).getReservation().getReservationId();
        users.subList(2, 5).forEach(u -> reserve(u, capacityId));   // 대기 3명

        Queue<Throwable> failures = runTogether(
                () -> reservationService.cancelReservation(users.get(0).getUserId(), r1),
                () -> reservationService.cancelReservation(users.get(1).getUserId(), r2));

        assertThat(failures).isEmpty();
        // 둘이 같은 1번을 깨우면 자리 하나가 증발한다
        assertThat(waitingEntryRepository.findAll())
                .extracting(WaitingEntry::getStatus)
                .containsExactlyInAnyOrder(WaitingStatus.OFFERED, WaitingStatus.OFFERED, WaitingStatus.WAITING);
        assertThat(stock(capacityId)).isEqualTo("0");
    }

    @Test
    @DisplayName("마감된 슬롯은 자리를 넘겨받았어도 확정할 수 없다")
    void confirm_afterClosed_throws() {
        String capacityId = saveSlot(saveStore(), tomorrowAt(19), 1);
        List<User> users = saveUsers(2);
        String reservationId = reserve(users.get(0), capacityId).getReservation().getReservationId();
        String waitingId = reserve(users.get(1), capacityId).getWaiting().getWaitingId();
        reservationService.cancelReservation(users.get(0).getUserId(), reservationId);
        moveSlotTo(capacityId, LocalDateTime.now().plusMinutes(30));

        assertThatThrownBy(() -> waitingService.confirm(users.get(1).getUserId(), waitingId))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("예약이 마감된 시간대입니다.");

        assertThat(reservationRepository.findAll())
                .noneMatch(r -> r.getStatus() == ReservationStatus.CONFIRMED);
    }

    @Test
    @DisplayName("마감된 슬롯에서 취소가 나오면 대기자에게 넘기지 않고 공용 재고로 돌린다")
    void cancel_afterClosed_doesNotOffer() {
        String capacityId = saveSlot(saveStore(), tomorrowAt(19), 1);
        List<User> users = saveUsers(2);
        String reservationId = reserve(users.get(0), capacityId).getReservation().getReservationId();
        String waitingId = reserve(users.get(1), capacityId).getWaiting().getWaitingId();
        moveSlotTo(capacityId, LocalDateTime.now().plusMinutes(30));

        reservationService.cancelReservation(users.get(0).getUserId(), reservationId);

        // 넘겨봤자 확정을 못 하니 5분 동안 자리만 묶인다
        assertThat(waiting(waitingId).getStatus()).isEqualTo(WaitingStatus.WAITING);
        assertThat(stock(capacityId)).isEqualTo("1");
    }

    @Test
    @DisplayName("배치는 마감된 슬롯의 대기만 끝내고, 아직 열린 슬롯의 줄은 그대로 둔다")
    void closeEndedLines_expiresOnlyClosedSlots() {
        Store store = saveStore();
        String closedId = saveSlot(store, tomorrowAt(19), 1);
        String openId = saveSlot(store, tomorrowAt(20), 1);
        List<User> users = saveUsers(4);
        reserve(users.get(0), closedId);
        String closedWaiting = reserve(users.get(1), closedId).getWaiting().getWaitingId();
        reserve(users.get(2), openId);
        String openWaiting = reserve(users.get(3), openId).getWaiting().getWaitingId();
        moveSlotTo(closedId, LocalDateTime.now().plusMinutes(30));

        scheduler.closeEndedLines();

        assertThat(waiting(closedWaiting).getStatus()).isEqualTo(WaitingStatus.EXPIRED);
        assertThat(waiting(openWaiting).getStatus()).isEqualTo(WaitingStatus.WAITING);
    }

    @Test
    @DisplayName("내 대기 순번을 조회한다 - 줄 선 순서대로 번호가 매겨진다")
    void myWaitings_showsPosition() {
        String capacityId = saveSlot(saveStore(), tomorrowAt(19), 1);
        List<User> users = saveUsers(3);
        reserve(users.get(0), capacityId);          // RESERVED
        reserve(users.get(1), capacityId);          // 1번
        reserve(users.get(2), capacityId);          // 2번

        List<MyWaitingResponse> first = waitingService.getMyWaitings(users.get(1).getUserId());
        assertThat(first).hasSize(1);
        assertThat(first.get(0).getStatus()).isEqualTo("WAITING");
        assertThat(first.get(0).getWaitingNumber()).isEqualTo(1);

        assertThat(waitingService.getMyWaitings(users.get(2).getUserId()).get(0).getWaitingNumber())
                .isEqualTo(2);
    }

    @Test
    @DisplayName("앞사람이 빠지면 순번이 당겨진다")
    void myWaitings_movesUpWhenAheadLeaves() {
        String capacityId = saveSlot(saveStore(), tomorrowAt(19), 1);
        List<User> users = saveUsers(4);
        String reservationId = reserve(users.get(0), capacityId).getReservation().getReservationId();
        String firstWaiting = reserve(users.get(1), capacityId).getWaiting().getWaitingId();
        reserve(users.get(2), capacityId);
        reserve(users.get(3), capacityId);

        assertThat(waitingService.getMyWaitings(users.get(3).getUserId()).get(0).getWaitingNumber())
                .isEqualTo(3);

        // 1번이 자리를 받았다가 기한을 넘겨 탈락 → 2번이 당겨짐
        reservationService.cancelReservation(users.get(0).getUserId(), reservationId);
        expireNow(firstWaiting);
        scheduler.expireOffers();

        // 3번 앞에는 이제 OFFERED 가 된 2번 하나뿐
        assertThat(waitingService.getMyWaitings(users.get(3).getUserId()).get(0).getWaitingNumber())
                .isEqualTo(2);
    }

    @Test
    @DisplayName("내 차례(OFFERED)면 번호는 0, 확정 기한을 함께 준다")
    void myWaitings_offeredShowsDeadline() {
        String capacityId = saveSlot(saveStore(), tomorrowAt(19), 1);
        List<User> users = saveUsers(2);
        String reservationId = reserve(users.get(0), capacityId).getReservation().getReservationId();
        reserve(users.get(1), capacityId);
        reservationService.cancelReservation(users.get(0).getUserId(), reservationId);

        MyWaitingResponse mine = waitingService.getMyWaitings(users.get(1).getUserId()).get(0);

        assertThat(mine.getStatus()).isEqualTo("OFFERED");
        assertThat(mine.getWaitingNumber()).isZero();
        assertThat(mine.getOfferExpiresAt())
                .isBetween(LocalDateTime.now().plusMinutes(4), LocalDateTime.now().plusMinutes(5));
    }

    // ---------- 헬퍼 ----------

    private ReserveResponse reserve(User user, String capacityId) {
        return reservationService.reserve(user.getUserId(), request(capacityId));
    }

    private WaitingEntry waiting(String waitingId) {
        return waitingEntryRepository.findById(waitingId).orElseThrow();
    }

    private String stock(String capacityId) {
        return redisTemplate.opsForValue().get(RedisKeys.stock(capacityId));
    }

    // 엔티티에 setter 가 없어서 기한만 SQL 로 과거로 돌린다
    private void expireNow(String waitingId) {
        jdbcTemplate.update("update waiting_entry set offer_expires_at = ? where waiting_id = ?",
                LocalDateTime.now().minusSeconds(1), waitingId);
    }

    // 작업들을 같은 순간에 출발시킨다
    private Queue<Throwable> runTogether(Runnable... tasks) throws InterruptedException {
        ExecutorService pool = Executors.newFixedThreadPool(tasks.length);
        CountDownLatch start = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(tasks.length);
        Queue<Throwable> failures = new ConcurrentLinkedQueue<>();

        for (Runnable task : tasks) {
            pool.submit(() -> {
                try {
                    start.await();
                    task.run();
                } catch (Throwable e) {
                    failures.add(e);
                } finally {
                    done.countDown();
                }
            });
        }

        start.countDown();
        assertThat(done.await(30, TimeUnit.SECONDS)).as("30초 안에 끝나야 함").isTrue();
        pool.shutdownNow();
        return failures;
    }
}
