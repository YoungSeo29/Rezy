package com.rezy.rezy.reservation.dto;

import lombok.Getter;

// 줄 서기 전 미리보기 - "지금 서면 몇 번째인지"
// 추정치다. 실제 확정 번호는 줄 선 직후(enqueue) 응답이 정답
@Getter
public class WaitingPreviewResponse {

    private final String slotCapacityId;
    private final long waitingCount;   // 현재 활성 대기 인원(WAITING + OFFERED)
    private final long nextNumber;     // 지금 서면 받을 번호 = waitingCount + 1

    private WaitingPreviewResponse(String slotCapacityId, long waitingCount, long nextNumber) {
        this.slotCapacityId = slotCapacityId;
        this.waitingCount = waitingCount;
        this.nextNumber = nextNumber;
    }

    public static WaitingPreviewResponse of(String slotCapacityId, long waitingCount) {
        return new WaitingPreviewResponse(slotCapacityId, waitingCount, waitingCount + 1);
    }
}
