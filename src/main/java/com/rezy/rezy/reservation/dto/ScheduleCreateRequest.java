package com.rezy.rezy.reservation.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.FutureOrPresent;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

@Getter
@NoArgsConstructor
public class ScheduleCreateRequest {

    @NotNull @FutureOrPresent
    private LocalDate startDate;

    @NotNull
    private LocalDate endDate;

    @NotEmpty
    private List<DayOfWeek> daysOfWeek;  //[Mon,Tue,Wed ...]

    @NotNull
    private LocalTime openTime;

    @NotNull
    private LocalTime closeTime;

    @Positive
    private int intervalMinutes;

    @NotEmpty @Valid
    private List<PartySizeCapacityRequest> capacities;
}
