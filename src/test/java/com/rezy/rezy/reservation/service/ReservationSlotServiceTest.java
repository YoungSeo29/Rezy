package com.rezy.rezy.reservation.service;

import com.rezy.rezy.reservation.domain.ReservationSlot;
import com.rezy.rezy.reservation.domain.SlotCapacity;
import com.rezy.rezy.reservation.dto.PartySizeCapacityRequest;
import com.rezy.rezy.reservation.dto.ScheduleCreateRequest;
import com.rezy.rezy.reservation.dto.ScheduleCreateResponse;
import com.rezy.rezy.reservation.repository.ReservationSlotRepository;
import com.rezy.rezy.store.StoreRepository;
import com.rezy.rezy.store.domain.Store;
import com.rezy.rezy.user.domain.User;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.AssertionsForInterfaceTypes.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
public class ReservationSlotServiceTest {

    @Mock StoreRepository storeRepository;
    @Mock ReservationSlotRepository reservationSlotRepository;

    @InjectMocks ReservationSlotService reservationSlotService;

    @Captor ArgumentCaptor<List<ReservationSlot>> slotsCaptor;

    @Test
    @DisplayName("지정한 요일, 운영시간, 간격대로 슬롯이 생성되고, 마감 시각은 비포함")
    void createSchedule_slotCountAndTimes() {

        // given
        // 기간 2026-10-05(월) ~ 10-11(일), 월수금 -> 대상 날짜 3일
        // 운영 11:00 ~ 14:00ㅡ 30분 간격, 하루 6개
        // 인원 2,4,6인 -> 슬롯마다 버킷 3개
        Store store = Store.create("테스트식당", "서울", "11:00-14:00", User.createLocal("m@test.com", "pw"));

        given(storeRepository.findByManagerUserId("manager-1")).willReturn(Optional.of(store));
        given(reservationSlotRepository.existsByStore(store)).willReturn(false);

        ScheduleCreateRequest request = scheduleRequest(
                LocalDate.of(2026, 10, 5), LocalDate.of(2026, 10, 11),
                List.of(DayOfWeek.MONDAY, DayOfWeek.WEDNESDAY, DayOfWeek.FRIDAY),
                LocalTime.of(11, 0), LocalTime.of(14, 0), 30,
                List.of(capacity(2, 3), capacity(4, 2), capacity(6, 1)));

        // when
        ScheduleCreateResponse response = reservationSlotService.createSchedule("manager-1", request);

        // then
        verify(reservationSlotRepository).saveAll(slotsCaptor.capture());
        List<ReservationSlot> slots = slotsCaptor.getValue();

        // 3일 × 6개 = 18개
        assertThat(slots).hasSize(18);
        assertThat(response.getCreateSlotCount()).isEqualTo(18);

        // 첫 슬롯 = 월요일 오픈 시각, 마지막 슬롯 = 금요일 마감 30분 전
        assertThat(slots.get(0).getSlotDatetime()).isEqualTo(LocalDateTime.of(2026, 10, 5, 11, 0));
        assertThat(slots.get(17).getSlotDatetime()).isEqualTo(LocalDateTime.of(2026, 10, 9, 13, 30));

        // 지정 안 한 요일(화·목·토·일)은 생성되지 않음
        assertThat(slots).extracting(s -> s.getSlotDatetime().getDayOfWeek())
                .containsOnly(DayOfWeek.MONDAY, DayOfWeek.WEDNESDAY, DayOfWeek.FRIDAY);

        // 경계값 - 마감 시각(14:00) 슬롯은 없어야 함
        assertThat(slots).extracting(s -> s.getSlotDatetime().toLocalTime())
                .doesNotContain(LocalTime.of(14, 0));

        // 모든 슬롯에 2·4·6인 버킷이 붙음
        assertThat(slots).allSatisfy(slot ->
                assertThat(slot.getCapacities())
                        .extracting(SlotCapacity::getPartySize)
                        .containsExactly(2, 4, 6));

    }

    // DTO 에 setter 가 없어서 리플렉션으로 채움
    private ScheduleCreateRequest scheduleRequest(LocalDate start, LocalDate end, List<DayOfWeek> days,
                                                  LocalTime open, LocalTime close, int interval,
                                                  List<PartySizeCapacityRequest> capacities) {
        ScheduleCreateRequest r = new ScheduleCreateRequest();
        ReflectionTestUtils.setField(r, "startDate", start);
        ReflectionTestUtils.setField(r, "endDate", end);
        ReflectionTestUtils.setField(r, "daysOfWeek", days);
        ReflectionTestUtils.setField(r, "openTime", open);
        ReflectionTestUtils.setField(r, "closeTime", close);
        ReflectionTestUtils.setField(r, "intervalMinutes", interval);
        ReflectionTestUtils.setField(r, "capacities", capacities);
        return r;
    }

    private PartySizeCapacityRequest capacity(int partySize, int teamCount) {
        PartySizeCapacityRequest c = new PartySizeCapacityRequest();
        ReflectionTestUtils.setField(c, "partySize", partySize);
        ReflectionTestUtils.setField(c, "teamCount", teamCount);
        return c;
    }

}
