package com.rezy.rezy.reservation.dto;

import com.rezy.rezy.reservation.domain.WaitingEntry;
import lombok.Getter;

@Getter
public class WaitingResponse {

    private final String waitingId;
    private final String slotCapacityId;
    private final long waitingNumber;   // 줄 선 시점의 번호표. 앞사람이 빠져도 다시 계산하지 않는다
    private final String status;

    public WaitingResponse(String waitingId, String slotCapacityId, long waitingNumber, String status) {
        this.waitingId = waitingId;
        this.slotCapacityId = slotCapacityId;
        this.waitingNumber = waitingNumber;
        this.status = status;
    }

    public static WaitingResponse of(WaitingEntry entry, long waitingNumber) {
        return new WaitingResponse(
                entry.getWaitingId(),
                entry.getCapacity().getSlotCapacityId(),
                waitingNumber,
                entry.getStatus().name()
        );
    }
}
