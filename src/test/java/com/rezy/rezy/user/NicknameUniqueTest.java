package com.rezy.rezy.user;

import com.rezy.rezy.support.IntegrationTestSupport;
import com.rezy.rezy.user.domain.User;
import com.rezy.rezy.user.domain.UserRole;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.List;
import java.util.Queue;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

// 통합 테스트 - 같은 닉네임을 동시에 저장하면 유니크 제약이 딱 1건만 통과시키는지 검증
// 애플리케이션 선검사만으로는 못 막는 경쟁 구간을 DB 제약 + 예외 변환이 막는다
class NicknameUniqueTest extends IntegrationTestSupport {

    @Autowired UserService userService;

    @Test
    @DisplayName("서로 다른 두 유저가 같은 닉네임으로 동시에 저장하면 1건만 성공하고 나머지는 409로 막힌다")
    void sameNickname_concurrent_onlyOneSucceeds() throws InterruptedException {
        // given - 아직 닉네임 없는 유저 2명
        List<User> users = saveUsers(2);
        String nickname = "중복닉";

        // when - 둘이 같은 순간에 같은 닉네임으로 프로필 완성
        ConcurrentResult result = runConcurrently(users.stream()
                .map(u -> (Runnable) () -> userService.completeProfile(u.getUserId(), nickname, UserRole.USER))
                .toList());

        // then - 하나만 성공, 나머지는 "이미 사용 중" 으로 막힌다
        assertThat(result.success()).isEqualTo(1);
        assertThat(result.failures()).hasSize(1);
        assertThat(result.failures().get(0))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("이미 사용 중인 닉네임입니다.");

        // DB 에도 그 닉네임은 딱 1명
        Integer count = jdbcTemplate.queryForObject(
                "select count(*) from users where nickname = ?", Integer.class, nickname);
        assertThat(count).isEqualTo(1);
    }

    // 여러 작업을 "동시에 출발"시키고 성공 수와 실패 예외들을 모아 돌려준다
    private ConcurrentResult runConcurrently(List<Runnable> tasks) throws InterruptedException {
        int n = tasks.size();
        ExecutorService pool = Executors.newFixedThreadPool(n);

        CountDownLatch ready = new CountDownLatch(n);
        CountDownLatch start = new CountDownLatch(1);
        CountDownLatch done  = new CountDownLatch(n);

        AtomicInteger success = new AtomicInteger();
        Queue<Throwable> failures = new ConcurrentLinkedQueue<>();

        for (Runnable task : tasks) {
            pool.submit(() -> {
                ready.countDown();
                try {
                    start.await();      // 신호 전까지 대기 → 최대한 같은 순간에 출발
                    task.run();
                    success.incrementAndGet();
                } catch (Throwable e) {
                    failures.add(e);
                } finally {
                    done.countDown();
                }
            });
        }

        ready.await();
        start.countDown();
        boolean finished = done.await(30, TimeUnit.SECONDS);
        pool.shutdownNow();

        assertThat(finished).as("30초 안에 모든 요청이 끝나야 함").isTrue();
        return new ConcurrentResult(success.get(), List.copyOf(failures));
    }

    record ConcurrentResult(int success, List<Throwable> failures) {}
}
