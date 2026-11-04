package com.rezy.rezy.reservation.dto;

import lombok.Getter;

// 예약 요청 결과 - 자리를 얻었으면 reservation, 만석이라 줄을 섰으면 waiting 만 채워진다
@Getter
public class ReserveResponse {

    private final String result;   // RESERVED | WAITING
    private final ReservationResponse reservation;
    private final WaitingResponse waiting;

    private ReserveResponse(String result, ReservationResponse reservation, WaitingResponse waiting) {
        this.result = result;
        this.reservation = reservation;
        this.waiting = waiting;
    }

    public static ReserveResponse reserved(ReservationResponse reservation) {
        return new ReserveResponse("RESERVED", reservation, null);
    }

    public static ReserveResponse waiting(WaitingResponse waiting) {
        return new ReserveResponse("WAITING", null, waiting);
    }
}
