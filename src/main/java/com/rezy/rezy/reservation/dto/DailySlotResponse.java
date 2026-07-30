package com.rezy.rezy.reservation.dto;

import lombok.Getter;

import java.time.LocalDate;
import java.util.List;

// 날짜 하나의 전체 응답
@Getter
public class DailySlotResponse {

    private final String storeId;
    private final String storeName;
    private final LocalDate date;
    private final List<SlotResponse> slots;

    public DailySlotResponse(String storeId, String storeName,
                             LocalDate date, List<SlotResponse> slots) {
        this.storeId = storeId;
        this.storeName = storeName;
        this.date = date;
        this.slots = slots;
    }

    // 프론트가 시간대 버튼 그릴 때 사용
    public static DailySlotResponse of(String storeId, String storeName,
                                       LocalDate date, List<SlotResponse> slots) {
        return new DailySlotResponse(storeId, storeName, date, slots);
    }

}
