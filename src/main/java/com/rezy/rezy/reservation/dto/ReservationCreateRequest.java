package com.rezy.rezy.reservation.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
public class ReservationCreateRequest {

    @NotBlank
    private String slotCapacityId;  // 예약할 튜플의 ID
}
