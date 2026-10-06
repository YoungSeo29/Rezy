package com.rezy.rezy.reservation;

import com.rezy.rezy.global.redis.RedisKeys;
import com.rezy.rezy.reservation.batch.StockReconciliationBatch;
import com.rezy.rezy.store.domain.Store;
import com.rezy.rezy.support.IntegrationTestSupport;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.script.DefaultRedisScript;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

public class StockReconciliationBatchTest extends IntegrationTestSupport {

    @Autowired
    StockReconciliationBatch batch;
    @Autowired
    DefaultRedisScript<Long> stockCasScript;

    // 내일 저녁 - 검사 범위(오늘 00:00 ~ 7일 후) 안에 들어간다
    private LocalDateTime targetTime() {
        return LocalDate.now().plusDays(1).atTime(19, 0);
    }

    @Test
    @DisplayName("값이 일치하면 아무것도 바꾸지 않는다")
    void matched_noChange() {
        Store store = saveStore();
        String capacityId = saveSlot(store, targetTime(), 3);   // 총정원 3, 예약 0 → 기대 3
        String key = RedisKeys.stock(capacityId);
        redisTemplate.opsForValue().set(key, "3");

        batch.reconcile();

        assertThat(redisTemplate.opsForValue().get(key)).isEqualTo("3");
    }

    @Test
    @DisplayName("불일치를 처음 본 주기에는 보정하지 않는다")
    void firstMismatch_notCorrected() {
        Store store = saveStore();
        String capacityId = saveSlot(store, targetTime(), 3);
        String key = RedisKeys.stock(capacityId);
        redisTemplate.opsForValue().set(key, "1");              // 기대 3 인데 1

        batch.reconcile();

        // 진행 중인 예약 때문에 생긴 착시일 수 있으므로 한 주기는 지켜본다
        assertThat(redisTemplate.opsForValue().get(key)).isEqualTo("1");
    }

    @Test
    @DisplayName("같은 불일치가 두 주기 연속이면 보정한다")
    void secondMismatch_corrected() {
        Store store = saveStore();
        String capacityId = saveSlot(store, targetTime(), 3);
        String key = RedisKeys.stock(capacityId);
        redisTemplate.opsForValue().set(key, "1");

        batch.reconcile();   // 1주기 - 기록만
        batch.reconcile();   // 2주기 - 보정

        assertThat(redisTemplate.opsForValue().get(key)).isEqualTo("3");
    }

    @Test
    @DisplayName("키가 없으면 건너뛰고, 키를 새로 만들지도 않는다")
    void noKey_skipped() {
        Store store = saveStore();
        String capacityId = saveSlot(store, targetTime(), 3);
        String key = RedisKeys.stock(capacityId);

        batch.reconcile();
        batch.reconcile();

        // 키 생성은 예약 시점의 loadStockIfAbsent 책임이다
        assertThat(redisTemplate.hasKey(key)).isFalse();
    }

    @Test
    @DisplayName("CAS - 읽은 값과 현재 값이 다르면 교체하지 않는다")
    void casFails_whenValueChanged() {
        Store store = saveStore();
        String capacityId = saveSlot(store, targetTime(), 3);
        String key = RedisKeys.stock(capacityId);
        redisTemplate.opsForValue().set(key, "2");

        // "1 이었을 때만 3 으로 바꿔라" 인데 실제 값은 2 → 바꾸면 안 됨
        Long result = redisTemplate.execute(stockCasScript, List.of(key), "1", "3");

        assertThat(result).isEqualTo(0L);
        assertThat(redisTemplate.opsForValue().get(key)).isEqualTo("2");
    }

}
