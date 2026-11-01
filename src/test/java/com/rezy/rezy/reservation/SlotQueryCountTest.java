package com.rezy.rezy.reservation;

import com.rezy.rezy.global.redis.RedisKeys;
import com.rezy.rezy.reservation.domain.ReservationSlot;
import com.rezy.rezy.reservation.dto.DailySlotResponse;
import com.rezy.rezy.reservation.service.ReservationSlotService;
import com.rezy.rezy.store.domain.Store;
import com.rezy.rezy.support.IntegrationTestSupport;
import jakarta.persistence.EntityManagerFactory;
import org.hibernate.SessionFactory;
import org.hibernate.stat.Statistics;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.LocalDate;
import java.util.List;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;

// N+1 회귀 방지 테스트
// 누가 나중에 fetch join 을 지우거나 연관관계를 바꾸면, 쿼리 수가 늘어나서 이 테스트가 바로 실패한다
// 재고 키가 Redis 에 있을 때와 없을 때 경로가 다르므로 둘 다 본다
class SlotQueryCountTest extends IntegrationTestSupport {

    @Autowired ReservationSlotService reservationSlotService;
    @Autowired EntityManagerFactory entityManagerFactory;

    private static final LocalDate DATE = LocalDate.of(2026, 10, 1);

    @ParameterizedTest(name = "시간대 {0}개 → 쿼리 2개")
    @ValueSource(ints = {1, 12, 22})
    @DisplayName("재고 키가 Redis 에 있으면 시간대 수와 무관하게 쿼리 2개로 고정된다")
    void slotQuery_stockInRedis(int slotCount) {
        Store store = saveStore();
        putStockInRedis(saveSlots(store, slotCount));

        // 가게 조회 1 + 슬롯·버킷 fetch join 1 = 2
        // fetch join 이 빠지면 1 + 1 + slotCount 로 늘어난다 (개선 전 22시간대 = 24개)
        assertThat(countQueriesOfSlotQuery(store, slotCount)).isEqualTo(2L);
    }

    @ParameterizedTest(name = "시간대 {0}개 → 쿼리 3개")
    @ValueSource(ints = {1, 12, 22})
    @DisplayName("재고 키가 없으면 CONFIRMED 집계 쿼리 1개만 더해진다")
    void slotQuery_stockMissing(int slotCount) {
        Store store = saveStore();
        saveSlots(store, slotCount);

        // 위 2개 + 키 없는 버킷의 CONFIRMED 수를 한 번에 세는 쿼리 1개 = 3
        // 버킷마다 세도록 되돌아가면 여기서 바로 터진다
        assertThat(countQueriesOfSlotQuery(store, slotCount)).isEqualTo(3L);
    }

    // 하루에 slotCount 개 시간대, 시간대마다 2·4·6인 버킷
    private List<ReservationSlot> saveSlots(Store store, int slotCount) {
        return IntStream.range(0, slotCount)
                .mapToObj(i -> {
                    ReservationSlot slot = ReservationSlot.create(store, DATE.atTime(11, 0).plusMinutes(30L * i));
                    slot.addCapacity(2, 3);
                    slot.addCapacity(4, 2);
                    slot.addCapacity(6, 1);
                    return reservationSlotRepository.save(slot);
                })
                .toList();
    }

    // 예약이 한 번이라도 일어난 버킷은 Redis 에 재고 키가 있다 (평소 경로)
    private void putStockInRedis(List<ReservationSlot> slots) {
        slots.stream()
                .flatMap(slot -> slot.getCapacities().stream())
                .forEach(capacity -> redisTemplate.opsForValue()
                        .set(RedisKeys.stock(capacity.getSlotCapacityId()),
                                String.valueOf(capacity.getTotalTeams())));
    }

    // 조회하면서 나간 쿼리 수만 센다
    // Hibernate 통계는 application.yml 의 generate_statistics: true 덕분에 쓸 수 있다
    private long countQueriesOfSlotQuery(Store store, int expectedSlotCount) {
        Statistics stats = entityManagerFactory.unwrap(SessionFactory.class).getStatistics();
        stats.clear();   // 데이터 준비 중에 나간 쿼리는 빼고, 조회 쿼리만 센다

        DailySlotResponse response = reservationSlotService.getSlotsByDate(store.getStoreId(), DATE);

        assertThat(response.getSlots()).hasSize(expectedSlotCount);
        return stats.getPrepareStatementCount();
    }
}
