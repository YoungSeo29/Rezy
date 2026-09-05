package com.rezy.rezy.reservation.dto;

import com.rezy.rezy.reservation.domain.ReservationSlot;
import com.rezy.rezy.reservation.domain.SlotCapacity;
import lombok.Getter;

import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

// 시간대 하나 + 버킷 목록
@Getter
public class SlotResponse {

    private final LocalDateTime slotDateTime;
    private final List<SlotCapacityResponse> capacities;

    public SlotResponse(LocalDateTime slotDateTime, List<SlotCapacityResponse> capacities) {
        this.slotDateTime = slotDateTime;
        this.capacities = capacities;
    }

    // 슬롯 하나를 시간+인원 목록으로 변환 - 인원수 오름차순으로 정렬해서 반환
    // 잔여 수량은 Redis 값으로 채움
    public static SlotResponse from (ReservationSlot slot, Map<String, Integer> stockMap) {
        List<SlotCapacityResponse> capacities = slot.getCapacities().stream()
                .sorted(Comparator.comparingInt(c -> c.getPartySize()))
                .map(c -> SlotCapacityResponse.from(c, stockMap.get(c.getSlotCapacityId())))
                .toList();

        return new SlotResponse(slot.getSlotDatetime(), capacities);
    }
}
