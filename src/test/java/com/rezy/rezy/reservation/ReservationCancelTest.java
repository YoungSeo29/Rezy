package com.rezy.rezy.reservation;

import com.rezy.rezy.global.redis.RedisKeys;
import com.rezy.rezy.reservation.domain.Reservation;
import com.rezy.rezy.reservation.domain.ReservationStatus;
import com.rezy.rezy.reservation.domain.WaitingStatus;
import com.rezy.rezy.reservation.dto.ReservationResponse;
import com.rezy.rezy.reservation.service.ReservationService;
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

class ReservationCancelTest extends IntegrationTestSupport {

    @Autowired ReservationService reservationService;

    @Test
    @DisplayName("예약을 취소하면 Redis 재고가 1 복구되고, 같은 날 다시 예약할 수 있다")
    void cancel_restoresStock() {
        // given - 정원 3팀, 1건 예약해서 재고 2
        Store store = saveStore();
        String capacityId = saveSlot(store, tomorrowAt(19), 3);
        User user = saveUser("cancel@test.com");
        String stockKey = RedisKeys.stock(capacityId);

        ReservationResponse reserved = reservationService.reserve(user.getUserId(), request(capacityId)).getReservation();
        assertThat(redisTemplate.opsForValue().get(stockKey)).isEqualTo("2");

        // when
        reservationService.cancelReservation(user.getUserId(), reserved.getReservationId());

        // then - 재고 복구 + 상태 변경
        assertThat(redisTemplate.opsForValue().get(stockKey)).isEqualTo("3");
        assertThat(reservationRepository.findById(reserved.getReservationId()))
                .get()
                .extracting(Reservation::getStatus)
                .isEqualTo(ReservationStatus.CANCELLED);

        // 취소된 예약은 "하루 1건"에 안 잡혀야 한다 → 같은 날 다시 예약 가능
        assertThatCode(() -> reservationService.reserve(user.getUserId(), request(capacityId)))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("이미 취소한 예약을 다시 취소하면 예외가 발생하고, 재고는 한 번만 복구된다")
    void cancelTwice_restoresOnce() {
        Store store = saveStore();
        String capacityId = saveSlot(store, tomorrowAt(19), 3);
        User user = saveUser("twice@test.com");
        String stockKey = RedisKeys.stock(capacityId);

        ReservationResponse reserved = reservationService.reserve(user.getUserId(), request(capacityId)).getReservation();
        reservationService.cancelReservation(user.getUserId(), reserved.getReservationId());

        // when & then
        assertThatThrownBy(() -> reservationService.cancelReservation(user.getUserId(), reserved.getReservationId()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("이미 취소된 예약입니다.");

        // 4 가 되면 정원(3)보다 많이 팔 수 있게 된다
        assertThat(redisTemplate.opsForValue().get(stockKey)).isEqualTo("3");
    }

    @Test
    @DisplayName("이미 지난 예약은 취소할 수 없고, 재고도 그대로다")
    void cancelPast_throws() {
        String capacityId = saveSlot(saveStore(), tomorrowAt(19), 3);
        User user = saveUser("past@test.com");
        String reservationId = reservationService.reserve(user.getUserId(), request(capacityId))
                .getReservation().getReservationId();
        moveSlotTo(capacityId, LocalDateTime.now().minusHours(1));

        assertThatThrownBy(() -> reservationService.cancelReservation(user.getUserId(), reservationId))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("이미 지난 예약은 취소할 수 없습니다.");
        assertThat(redisTemplate.opsForValue().get(RedisKeys.stock(capacityId))).isEqualTo("2");
    }

    @Test
    @DisplayName("같은 예약을 동시에 두 번 취소하면 한 번만 성공하고, 재고는 정확히 1 복구된다")
    void concurrentCancel_restoresOnce() throws InterruptedException {
        String capacityId = saveSlot(saveStore(), tomorrowAt(19), 3);
        User user = saveUser("race@test.com");
        String reservationId = reservationService.reserve(user.getUserId(), request(capacityId))
                .getReservation().getReservationId();

        Queue<Throwable> failures = cancelTogether(user.getUserId(), reservationId, 2);

        assertThat(failures).hasSize(1);
        assertThat(failures.peek())
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("이미 취소된 예약입니다.");
        assertThat(redisTemplate.opsForValue().get(RedisKeys.stock(capacityId))).isEqualTo("3");
    }

    @Test
    @DisplayName("대기자가 있을 때 같은 예약을 동시에 두 번 취소해도 자리는 한 명에게만 넘어간다")
    void concurrentCancel_offersOnce() throws InterruptedException {
        String capacityId = saveSlot(saveStore(), tomorrowAt(19), 1);
        List<User> users = saveUsers(3);
        String reservationId = reservationService.reserve(users.get(0).getUserId(), request(capacityId))
                .getReservation().getReservationId();
        reservationService.reserve(users.get(1).getUserId(), request(capacityId));
        reservationService.reserve(users.get(2).getUserId(), request(capacityId));

        Queue<Throwable> failures = cancelTogether(users.get(0).getUserId(), reservationId, 2);

        assertThat(failures).hasSize(1);
        // 2명이 OFFERED 가 되면 정원 1팀에 자리 주인이 둘이 된다
        assertThat(waitingEntryRepository.countByCapacityAndStatus(capacityId, WaitingStatus.OFFERED)).isEqualTo(1);
        assertThat(waitingEntryRepository.countByCapacityAndStatus(capacityId, WaitingStatus.WAITING)).isEqualTo(1);
        assertThat(redisTemplate.opsForValue().get(RedisKeys.stock(capacityId))).isEqualTo("0");
    }

    private Queue<Throwable> cancelTogether(String userId, String reservationId, int times) throws InterruptedException {
        ExecutorService pool = Executors.newFixedThreadPool(times);
        CountDownLatch start = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(times);
        Queue<Throwable> failures = new ConcurrentLinkedQueue<>();

        for (int i = 0; i < times; i++) {
            pool.submit(() -> {
                try {
                    start.await();
                    reservationService.cancelReservation(userId, reservationId);
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
