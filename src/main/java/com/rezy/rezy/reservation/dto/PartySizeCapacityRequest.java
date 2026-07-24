package com.rezy.rezy.reservation.dto;

import jakarta.validation.constraints.Positive;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
public class PartySizeCapacityRequest {

    @Positive
    private int partySize;  // 인원수

    @Positive
    private int teamCount;  // 그 인원수로 받을 팀 수
}
