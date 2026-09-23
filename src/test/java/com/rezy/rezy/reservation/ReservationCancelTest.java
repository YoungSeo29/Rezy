package com.rezy.rezy.reservation;

import com.rezy.rezy.global.redis.RedisKeys;
import com.rezy.rezy.reservation.domain.Reservation;
import com.rezy.rezy.reservation.domain.ReservationStatus;
import com.rezy.rezy.reservation.dto.ReservationResponse;
import com.rezy.rezy.reservation.service.ReservationService;
import com.rezy.rezy.store.domain.Store;
import com.rezy.rezy.support.IntegrationTestSupport;
import com.rezy.rezy.user.domain.User;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.*;

class ReservationCancelTest extends IntegrationTestSupport {

    @Autowired ReservationService reservationService;

    @Test
    @DisplayName("예약을 취소하면 Redis 재고가 1 복구되고, 같은 날 다시 예약할 수 있다")
    void cancel_restoresStock() {
        // given - 정원 3팀, 1건 예약해서 재고 2
        Store store = saveStore();
        String capacityId = saveSlot(store, LocalDateTime.of(2026, 10, 1, 19, 0), 3);
        User user = saveUser("cancel@test.com");
        String stockKey = RedisKeys.stock(capacityId);

        ReservationResponse reserved = reservationService.reserve(user.getUserId(), request(capacityId));
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
        String capacityId = saveSlot(store, LocalDateTime.of(2026, 10, 1, 19, 0), 3);
        User user = saveUser("twice@test.com");
        String stockKey = RedisKeys.stock(capacityId);

        ReservationResponse reserved = reservationService.reserve(user.getUserId(), request(capacityId));
        reservationService.cancelReservation(user.getUserId(), reserved.getReservationId());

        // when & then
        assertThatThrownBy(() -> reservationService.cancelReservation(user.getUserId(), reserved.getReservationId()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("이미 취소된 예약입니다.");

        // 4 가 되면 정원(3)보다 많이 팔 수 있게 된다
        assertThat(redisTemplate.opsForValue().get(stockKey)).isEqualTo("3");
    }
}