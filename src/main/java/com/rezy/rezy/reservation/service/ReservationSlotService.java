package com.rezy.rezy.reservation.service;

import com.rezy.rezy.global.redis.RedisKeys;
import com.rezy.rezy.reservation.domain.SlotCapacity;
import com.rezy.rezy.reservation.dto.*;
import com.rezy.rezy.reservation.repository.ReservationSlotRepository;
import com.rezy.rezy.reservation.domain.ReservationSlot;
import com.rezy.rezy.store.StoreRepository;
import com.rezy.rezy.store.domain.Store;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.*;

// 슬롯 관리 & 조회용
// Redis 키를 만들지 않음. 키가 없는 capacity면 DB에서 읽어와서 대체
@Service
@RequiredArgsConstructor
public class ReservationSlotService {

    private final StoreRepository storeRepository;
    private final ReservationSlotRepository reservationSlotRepository;
    private final StringRedisTemplate redisTemplate;

    // 예약 스케줄 생성 - 내 가게를 찾아 예약 스케줄 이미 있는지 체크 후
    // 기간 전체 슬롯 만들어서 저장 + 생성 요약을 반환
    @Transactional
    public ScheduleCreateResponse createSchedule(String managerId, ScheduleCreateRequest request) {

        // 1) 로그인한 사장님 가게 조회 (없으면 가게 등록부터 해야된다는 안내)
        Store store = storeRepository.findByManagerUserId(managerId)
                .orElseThrow(() -> new IllegalStateException("가게를 먼저 등록해주세요"));

        // 2) 값 검증 (기간, 시간, 인원 규칙)
        validate(request);

        // 3) 해당 기간에 이미 슬롯 있으면 거절
        LocalDateTime rangeStart = request.getStartDate().atStartOfDay();
        LocalDateTime rangeEnd = request.getEndDate().atTime(LocalTime.MAX);

        if(reservationSlotRepository.existsByStore(store)) {
            throw new IllegalStateException("이미 예약 스케줄을 생성했습니다. 수정만 가능합니다.");
        }

        // 4) 슬롯 대량 생성
        List<ReservationSlot> slots = generateSlots(store, request);

        // 5) 저장
        reservationSlotRepository.saveAll(slots);

        return ScheduleCreateResponse.of(slots.size(), request.getStartDate(), request.getEndDate());
    }

    // 유효성 검사
    private void validate(ScheduleCreateRequest request) {

        if(request.getEndDate().isBefore(request.getStartDate())) {
            throw new IllegalArgumentException("종료일이 시작일보다 빠를 수 없습니다.");
        }
        if (request.getEndDate().isAfter(request.getStartDate().plusMonths(1))) {
            throw new IllegalArgumentException("예약 기간은 최대 한 달까지 설정할 수 있습니다.");
        }
        if (!request.getOpenTime().isBefore(request.getCloseTime())) {
            throw new IllegalArgumentException("오픈 시간이 마감 시간보다 빨라야 합니다.");
        }
        long distinctSizes = request.getCapacities().stream()
                .map(PartySizeCapacityRequest::getPartySize).distinct().count();
        if (distinctSizes != request.getCapacities().size()) {
            throw new IllegalArgumentException("같은 인원수가 중복으로 지정되었습니다.");
        }
    }

    // 슬롯 생성
    private List<ReservationSlot> generateSlots(Store store, ScheduleCreateRequest request) {

        List<ReservationSlot> slots = new ArrayList<>();
        Set<DayOfWeek> targetDays = new HashSet<>(request.getDaysOfWeek());

        int openMinute = request.getOpenTime().toSecondOfDay() / 60;
        int closeMinute = request.getCloseTime().toSecondOfDay() / 60;

        for (LocalDate date = request.getStartDate(); !date.isAfter(request.getEndDate()); date = date.plusDays(1)) {

            // 지정한 요일 아니면 pass
            if (!targetDays.contains(date.getDayOfWeek())) continue;

            //
            for (int m = openMinute; m < closeMinute; m += request.getIntervalMinutes()) {
                LocalDateTime slotDateTime = LocalDateTime.of(date, LocalTime.ofSecondOfDay(m * 60L));

                ReservationSlot slot = ReservationSlot.create(store, slotDateTime);

                for (PartySizeCapacityRequest cap : request.getCapacities()) {
                    slot.addCapacity(cap.getPartySize(), cap.getTeamCount());
                }

                slots.add(slot);
            }
        }

        return slots;
    }

    // 특정 날짜의 슬롯 조회 - 달력에서 날짜 눌렀을 때 시간대 + 잔여인원 반환
    // 슬롯 구조는 DB에서, 잔여 수량은 Redis에서 가져옴
    @Transactional(readOnly = true)
    public DailySlotResponse getSlotsByDate(String storeId, LocalDate date) {

        Store store = storeRepository.findById(storeId).orElseThrow(() -> new IllegalArgumentException("존재하지 않는 가게입니다."));

        // N+1 문제 방지를 위해 fetch join -> 한 번에 슬롯 + 인원 갖고옴
        List<ReservationSlot> slots = reservationSlotRepository
                .findWithCapacities(
                        store, date.atStartOfDay(), date.atTime(LocalTime.MAX));

        // 모든 slot에 대해, 각 capacities를 Redis 에서 한 번에 조회
        Map<String, Integer> stockMap = loadStocks(slots);

        // SlotResponses 예시
        // [
        //  {
        //    "slotDateTime": "2025-07-25T17:00:00",
        //    "capacities": [
        //      { "slotCapacityId": "cap-A", "partySize": 2, "remainingTeams": 1, "available": true },
        //      { "slotCapacityId": "cap-B", "partySize": 4, "remainingTeams": 2, "available": true }
        //    ]
        //  },
        //  {
        //    "slotDateTime": "2025-07-25T18:00:00",
        //    "capacities": [
        //      { "slotCapacityId": "cap-C", "partySize": 2, "remainingTeams": 3, "available": true },  // Redis에 없어서 DB값(3) 씀
        //      { "slotCapacityId": "cap-D", "partySize": 4, "remainingTeams": 0, "available": false }
        //    ]
        //  }
        //]
        List<SlotResponse> slotResponses = slots.stream()
                .map(slot -> SlotResponse.from(slot, stockMap))
                .toList();

        return DailySlotResponse.of(storeId, store.getStoreName(), date, slotResponses);
    }

    // 모든 슬롯에 딸린 모든 버킷의 잔여 수량을 Redis에서 한 방에 읽어옴
    private Map<String, Integer> loadStocks(List<ReservationSlot> slots) {

        // CapacityId 싹 모으기
        List<String> capacityIds = slots.stream()
                .flatMap(slot -> slot.getCapacities().stream())
                .map(SlotCapacity::getSlotCapacityId).
                toList();

        Map<String, Integer> stockMap = new HashMap<>();

        // 슬롯이 없는 날짜면 empty List로 호출하게 되므로 미리 빠져나감
        if(capacityIds.isEmpty()){
            return stockMap;
        }

        // Redis 키 형태로 변환
        List<String> keys = capacityIds.stream()
                .map(RedisKeys::stock)
                .toList();

        // Redis에서 한번에 조회 (multiGet)
        // ex) values = ["200", "3", null, "150", null, "5"]   null은 아직 예약된 적이 없어서 Redis에 값이 없음
        List<String> values = redisTemplate.opsForValue().multiGet(keys);

        if(values == null) {
            return stockMap;
        }

        // Map으로 조립
        // 키 없는 버킷(=예약된적 없는 버킷)은 null로 오는데, 그건 안담음
        // ex)
        // stockMap = {
        //    "capacity-A": 200,
        //    "capacity-B": 3,
        //    "capacity-D": 150,
        //    "capacity-F": 5
        //}
        for (int i = 0; i < capacityIds.size(); i++) {
            String value = values.get(i);
            if (value != null) {
                stockMap.put(capacityIds.get(i), Integer.parseInt(value));
            }
        }

        return stockMap;
    }

    // 예약 가능한 날짜 목록 - 달력에서 어떤 날을 활성화 할지 표시하기 위함
    @Transactional(readOnly = true)
    public List<LocalDate> getAvailableDates(String storeId, int year, int month) {

        Store store = storeRepository.findById(storeId).orElseThrow(() -> new IllegalArgumentException("존재하지 않는 가게입니다."));

        LocalDate first = LocalDate.of(year, month, 1);
        LocalDate last = first.withDayOfMonth(first.lengthOfMonth());

        return reservationSlotRepository.findSlotDatetimes(store, first.atStartOfDay(),
                last.atTime(LocalTime.MAX))
                .stream()
                .map(LocalDateTime::toLocalDate)
                .distinct()
                .sorted()
                .toList();
    }
}