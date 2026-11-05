package com.rezy.rezy.reservation.dto;

import com.rezy.rezy.reservation.domain.WaitingEntry;
import lombok.Getter;

import java.time.LocalDateTime;

// 내가 서 있는 줄 한 칸
// WAITING 이면 지금 몇 번째인지(waitingNumber), OFFERED 면 내 차례이고 언제까지인지(offerExpiresAt)
@Getter
public class MyWaitingResponse {

    private final String waitingId;
    private final String storeName;
    private final LocalDateTime slotDatetime;
    private final int partySize;
    private final String status;
    private final long waitingNumber;           // WAITING 일 때 1,2,3... / OFFERED 면 0 = 내 차례
    private final LocalDateTime offerExpiresAt;  // OFFERED 일 때만. 앱이 남은 시간 카운트다운에 쓴다

    private MyWaitingResponse(String waitingId, String storeName, LocalDateTime slotDatetime,
                              int partySize, String status, long waitingNumber, LocalDateTime offerExpiresAt) {
        this.waitingId = waitingId;
        this.storeName = storeName;
        this.slotDatetime = slotDatetime;
        this.partySize = partySize;
        this.status = status;
        this.waitingNumber = waitingNumber;
        this.offerExpiresAt = offerExpiresAt;
    }

    // 조회 시 아직 줄 서는 중(WAITING)이면 이걸로 변환 - 지금 내 번호
    public static MyWaitingResponse waiting(WaitingEntry entry, long waitingNumber) {
        return of(entry, waitingNumber, null);
    }

    // 조회 시 내 차례(OFFERED)면 이걸로 변환 - 번호 0, 확정 마감 시각 포함
    public static MyWaitingResponse offered(WaitingEntry entry) {
        return of(entry, 0, entry.getOfferExpiresAt());
    }

    private static MyWaitingResponse of(WaitingEntry entry, long waitingNumber, LocalDateTime offerExpiresAt) {
        return new MyWaitingResponse(
                entry.getWaitingId(),
                entry.getCapacity().getSlot().getStore().getStoreName(),
                entry.getCapacity().getSlot().getSlotDatetime(),
                entry.getCapacity().getPartySize(),
                entry.getStatus().name(),
                waitingNumber,
                offerExpiresAt
        );
    }
}
