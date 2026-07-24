package com.rezy.rezy.reservation.dto;

import lombok.Getter;

import java.time.LocalDate;

@Getter
public class ScheduleCreateResponse {

    private final int createSlotCount;
    private final LocalDate startDate;
    private final LocalDate endDate;

    public ScheduleCreateResponse(int createSlotCount, LocalDate startDate, LocalDate endDate) {
        this.createSlotCount = createSlotCount;
        this.startDate = startDate;
        this.endDate = endDate;
    }

    public static ScheduleCreateResponse of(int count, LocalDate startDate, LocalDate endDate) {
        return new ScheduleCreateResponse(count, startDate, endDate);
    }
}
