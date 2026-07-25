package com.rezy.rezy.reservation.dto;

import com.rezy.rezy.reservation.domain.Reservation;
import lombok.Getter;

import java.time.LocalDateTime;

@Getter
public class MyReservationResponse {

    private final String reservationId;
    private final String storeId;
    private final String storeName;
    private final String storeAddress;
    private final LocalDateTime slotDatetime;
    private final int partySize;
    private final String status;

    public MyReservationResponse(String reservationId, String storeId, String storeName,
                                 String storeAddress, LocalDateTime slotDatetime,
                                 int partySize, String status) {
        this.reservationId = reservationId;
        this.storeId = storeId;
        this.storeName = storeName;
        this.storeAddress = storeAddress;
        this.slotDatetime = slotDatetime;
        this.partySize = partySize;
        this.status = status;
    }

    // 예약 엔티티를 마이페이지 목록 항목으로 변환 — 가게 이름·주소까지 포함해 반환
    public static MyReservationResponse from(Reservation r) {
        return new MyReservationResponse(
                r.getReservationId(),
                r.getStore().getStoreId(),
                r.getStore().getStoreName(),
                r.getStore().getAddress(),
                r.getSlot().getSlotDatetime(),
                r.getPartySize(),
                r.getStatus().name()
        );
    }
}
