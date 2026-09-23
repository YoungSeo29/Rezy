package com.rezy.rezy.reservation.service;


import com.rezy.rezy.global.redis.RedisKeys;
import com.rezy.rezy.reservation.domain.ReservationStatus;
import com.rezy.rezy.reservation.dto.ReservationCreateRequest;
import com.rezy.rezy.reservation.repository.ReservationRepository;
import com.rezy.rezy.reservation.repository.SlotCapacityRepository;
import com.rezy.rezy.user.UserRepository;
import com.rezy.rezy.user.domain.User;
import com.rezy.rezy.user.domain.UserRole;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.*;

// 단위 테스트 - ReservationService 검증
// @Mock으로 만든 가짜 저장소가 @InjectMocks로 서비스 생성자에 주입됨
@ExtendWith(MockitoExtension.class)
public class ReservationServiceTest {

    @Mock UserRepository userRepository;
    @Mock SlotCapacityRepository slotCapacityRepository;
    @Mock ReservationRepository reservationRepository;
    @Mock StringRedisTemplate redisTemplate;
    @Mock ValueOperations<String, String> valueOperations;  // redisTemplate.opsForValue() 가 돌려줄 가짜

    @InjectMocks ReservationService reservationService;

    static final String USER_ID = "user-1";
    static final String CAPACITY_ID = "cap-1";
    static final LocalDateTime SLOT_TIME = LocalDateTime.of(2026, 10, 1, 19, 0);

    @Test
    @DisplayName("같은 날 이미 예약이 있으면 예외 발생, Redis 재고 건드리지 않음")
    void sameDayReservation_throws() {
        given(userRepository.findById(USER_ID)).willReturn(Optional.of(normalUser()));
        given(slotCapacityRepository.findSlotDatetimeById(CAPACITY_ID)).willReturn(Optional.of(SLOT_TIME));

        given(reservationRepository.existsActiveOnDate(
                USER_ID,
                LocalDateTime.of(2026, 10, 1, 0, 0),
                LocalDateTime.of(2026, 10, 2, 0, 0),
                ReservationStatus.CANCELLED))
                .willReturn(true);

        // when & then
        assertThatThrownBy(() -> reservationService.reserve(USER_ID, request(CAPACITY_ID)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("같은 날짜에는 하루에 한 건만 예약할 수 있습니다.");

        // 재고를 깎기 전에 막혀야 한다 → Redis 호출이 한 번도 없어야 함
        verifyNoInteractions(redisTemplate);

    }

    @Test
    @DisplayName("일반 사용자(USER)가 아니면 예약할 수 없다")
    void managerCannotReserve() {
        // given - 사장님 계정
        User manager = User.createLocal("manager@test.com", "pw");
        manager.completeProfile("사장님", UserRole.MANAGER);
        given(userRepository.findById(USER_ID)).willReturn(Optional.of(manager));

        // when & then
        assertThatThrownBy(() -> reservationService.reserve(USER_ID, request(CAPACITY_ID)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("예약은 일반 사용자만 가능합니다.");

        // 권한 검사에서 바로 끝나야 한다 → 이후 단계(날짜 조회, 중복 확인, Redis)는 실행 안 됨
        verifyNoInteractions(slotCapacityRepository, reservationRepository, redisTemplate);
    }

    @Test
    @DisplayName("DECR 결과가 음수면 INCR 로 재고를 되돌리고 예외가 발생한다")
    void negativeStock_rollbackAndThrow() {
        String stockKey = RedisKeys.stock(CAPACITY_ID);

        // given - 검증은 다 통과, Redis 재고는 이미 0 인 상황
        given(userRepository.findById(USER_ID)).willReturn(Optional.of(normalUser()));
        given(slotCapacityRepository.findSlotDatetimeById(CAPACITY_ID)).willReturn(Optional.of(SLOT_TIME));
        given(reservationRepository.existsActiveOnDate(any(), any(), any(), any())).willReturn(false);
        given(redisTemplate.hasKey(stockKey)).willReturn(true);             // 재고 키는 이미 있음
        given(redisTemplate.opsForValue()).willReturn(valueOperations);
        given(valueOperations.decrement(stockKey)).willReturn(-1L);         // 0 에서 깎여 -1

        // when & then
        assertThatThrownBy(() -> reservationService.reserve(USER_ID, request(CAPACITY_ID)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("잔여 좌석이 없습니다.");

        // 핵심 - 깎은 1 을 다시 올려놨는가. 안 하면 재고가 -1 로 남는다
        verify(valueOperations).increment(stockKey);
        // 예약은 저장되면 안 된다
        verify(reservationRepository, never()).save(any());
    }

    // createLocal 로 만든 유저는 role = USER
    private User normalUser() {
        return User.createLocal("user@test.com", "pw");
    }

    // DTO 에 setter/생성자가 없어서, 테스트에서만 리플렉션으로 필드를 채운다
    private ReservationCreateRequest request(String capacityId) {
        ReservationCreateRequest request = new ReservationCreateRequest();
        ReflectionTestUtils.setField(request, "slotCapacityId", capacityId);
        return request;
    }

}
