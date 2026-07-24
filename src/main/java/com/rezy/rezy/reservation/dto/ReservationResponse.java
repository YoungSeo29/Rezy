package com.rezy.rezy.reservation.dto;

import com.rezy.rezy.reservation.domain.Reservation;
import lombok.Getter;

import java.time.LocalDateTime;

@Getter
public class ReservationResponse {

    private final String reservationId;
    private final String storeId;
    private final LocalDateTime slotStartTime;
    private final int partySize;
    private final String status;

    public ReservationResponse(String reservationId, String storeId, LocalDateTime slotDateTime, int partySize, String status) {
        this.reservationId = reservationId;
        this.storeId = storeId;
        this.slotStartTime = slotDateTime;
        this.partySize = partySize;
        this.status = status;
    }

    // Reservation 엔티티 응답을 dto로 변환
    public static ReservationResponse from (Reservation reservation) {
        return new ReservationResponse(
                reservation.getReservationId(),
                reservation.getStore().getStoreId(),
                reservation.getSlot().getSlotDatetime(),
                reservation.getPartySize(),
                reservation.getStatus().name()
        );
    }
}
