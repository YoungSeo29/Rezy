package com.rezy.rezy.reservation;

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

import static org.assertj.core.api.Assertions.assertThat;

// N+1 회귀 방지 테스트
// 누가 나중에 fetch join 을 지우거나 연관관계를 바꾸면, 쿼리 수가 늘어나서 이 테스트가 바로 실패한다
class SlotQueryCountTest extends IntegrationTestSupport {

    @Autowired ReservationSlotService reservationSlotService;
    @Autowired EntityManagerFactory entityManagerFactory;

    @ParameterizedTest(name = "시간대 {0}개 → 쿼리 2개")
    @ValueSource(ints = {1, 12, 22})
    @DisplayName("슬롯 조회는 시간대 수와 무관하게 쿼리 2개로 고정된다")
    void slotQuery_fixedQueryCount(int slotCount) {
        // given - 하루에 slotCount 개 시간대, 시간대마다 2·4·6인 버킷
        Store store = saveStore();
        LocalDate date = LocalDate.of(2026, 10, 1);
        for (int i = 0; i < slotCount; i++) {
            ReservationSlot slot = ReservationSlot.create(store, date.atTime(11, 0).plusMinutes(30L * i));
            slot.addCapacity(2, 3);
            slot.addCapacity(4, 2);
            slot.addCapacity(6, 1);
            reservationSlotRepository.save(slot);
        }

        // Hibernate 가 실행한 쿼리 수를 세는 통계 (application.yml 의 generate_statistics: true 덕분에 사용 가능)
        // QueryCountFilter 가 쓰던 것과 같은 값이다
        Statistics stats = entityManagerFactory.unwrap(SessionFactory.class).getStatistics();
        stats.clear();   // 데이터 준비 중에 나간 쿼리는 빼고, 조회 쿼리만 센다

        // when
        DailySlotResponse response = reservationSlotService.getSlotsByDate(store.getStoreId(), date);

        // then
        assertThat(response.getSlots()).hasSize(slotCount);

        // 가게 조회 1 + 슬롯·버킷 fetch join 1 = 2
        // fetch join 이 빠지면 1 + 1 + slotCount 로 늘어난다 (개선 전 22시간대 = 24개)
        assertThat(stats.getPrepareStatementCount()).isEqualTo(2);
    }
}