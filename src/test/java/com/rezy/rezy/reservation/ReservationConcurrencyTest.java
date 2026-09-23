package com.rezy.rezy.reservation;

import com.rezy.rezy.global.redis.RedisKeys;
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
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

// 통합 테스트 - 실제 DB + Redis 에 동시에 요청을 쏴서 정합성 검증
// k6 부하테스트로 확인했던 "초과 예약 0건"을, 매번 자동으로 확인되는 테스트로 고정한다
class ReservationConcurrencyTest extends IntegrationTestSupport {

    @Autowired ReservationService reservationService;

    @Test
    @DisplayName("정원 3팀에 100명이 동시에 예약하면 정확히 3건만 성공하고 Redis 잔여는 0이다")
    void capacity3_100threads() throws InterruptedException {
        // given - 정원 3팀짜리 버킷 1개, 서로 다른 유저 100명
        Store store = saveStore();
        String capacityId = saveSlot(store, LocalDateTime.of(2026, 10, 1, 19, 0), 3);
        List<User> users = saveUsers(100);
        // Redis 는 비어있는 상태로 시작
        // → 100개 요청이 "재고 키 최초 적재(SETNX)" 경쟁까지 동시에 겪게 된다

        // when
        ConcurrentResult result = runConcurrently(users.stream()
                .map(u -> (Runnable) () -> reservationService.reserve(u.getUserId(), request(capacityId)))
                .toList());

        // then
        assertThat(result.success()).isEqualTo(3);

        // 97건은 전부 "매진" 사유로만 실패해야 한다 (데드락 같은 다른 에러가 섞이면 안 됨)
        assertThat(result.failures()).hasSize(97)
                .allSatisfy(e -> assertThat(e)
                        .isInstanceOf(IllegalStateException.class)
                        .hasMessage("잔여 좌석이 없습니다."));

        assertThat(reservationRepository.count()).isEqualTo(3);                                  // DB 에도 3건
        assertThat(redisTemplate.opsForValue().get(RedisKeys.stock(capacityId))).isEqualTo("0"); // 음수로 새지 않음
    }

    @Test
    @DisplayName("같은 유저가 같은 날 다른 시간대 2건을 동시에 예약하면 1건만 성공한다")
    void sameUser_sameDay_concurrent() throws InterruptedException {
        // given - 같은 날 점심·저녁 슬롯 (정원은 넉넉히 3팀 → 정원이 아니라 "하루 1건" 규칙만 검증)
        Store store = saveStore();
        String lunch  = saveSlot(store, LocalDateTime.of(2026, 10, 1, 12, 0), 3);
        String dinner = saveSlot(store, LocalDateTime.of(2026, 10, 1, 19, 0), 3);
        User user = saveUser("same@test.com");

        // when - 같은 유저가 두 슬롯에 동시에 예약
        ConcurrentResult result = runConcurrently(List.of(
                () -> reservationService.reserve(user.getUserId(), request(lunch)),
                () -> reservationService.reserve(user.getUserId(), request(dinner))));

        // then
        assertThat(result.success()).isEqualTo(1);
        assertThat(reservationRepository.count()).isEqualTo(1);
    }

    // 여러 작업을 "동시에 출발"시키고, 성공 수와 실패 예외들을 모아서 돌려준다
    private ConcurrentResult runConcurrently(List<Runnable> tasks) throws InterruptedException {
        int n = tasks.size();
        ExecutorService pool = Executors.newFixedThreadPool(n);

        CountDownLatch ready = new CountDownLatch(n);   // 모든 스레드가 출발선에 섰는지
        CountDownLatch start = new CountDownLatch(1);   // 출발 신호
        CountDownLatch done  = new CountDownLatch(n);   // 모두 끝났는지

        AtomicInteger success = new AtomicInteger();
        Queue<Throwable> failures = new ConcurrentLinkedQueue<>();

        for (Runnable task : tasks) {
            pool.submit(() -> {
                ready.countDown();
                try {
                    start.await();       // 신호 올 때까지 대기 → 최대한 같은 순간에 출발
                    task.run();
                    success.incrementAndGet();
                } catch (Throwable e) {
                    failures.add(e);
                } finally {
                    done.countDown();
                }
            });
        }

        // 출발 신호 없이 그냥 돌리면, 먼저 뜬 스레드가 끝난 뒤에 다음 스레드가 시작돼서
        // 경쟁이 안 일어나고 "운 좋게" 통과할 수 있다
        ready.await();
        start.countDown();
        boolean finished = done.await(30, TimeUnit.SECONDS);
        pool.shutdownNow();

        assertThat(finished).as("30초 안에 모든 요청이 끝나야 함").isTrue();
        return new ConcurrentResult(success.get(), List.copyOf(failures));
    }

    record ConcurrentResult(int success, List<Throwable> failures) {}
}