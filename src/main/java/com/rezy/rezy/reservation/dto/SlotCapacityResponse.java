package com.rezy.rezy.reservation.dto;

import com.rezy.rezy.reservation.domain.SlotCapacity;
import lombok.Getter;

// 인원 버킷 하나
@Getter
public class SlotCapacityResponse {

    private final String slotCapacityId;
    private final int partySize;
    private final int remainingTeams;
    private final boolean available;

    public SlotCapacityResponse(String slotCapacityId, int partySize, int remainingTeams, boolean available) {
        this.slotCapacityId = slotCapacityId;
        this.partySize = partySize;
        this.remainingTeams = remainingTeams;
        this.available = available;
    }

    // Redis 잔여 값이 있으면 그걸 쓰고, 없으면 DB 값을 쓴다
    // key가 없다 = 해당 버킷에 예약이 한 번도 들어오지 않았다 = DB 값이 정확하다
    public static SlotCapacityResponse from(SlotCapacity capacity, Integer redisRemaining) {
        int remaining = (redisRemaining != null) ? redisRemaining : capacity.getRemainingTeams();
        return new SlotCapacityResponse(
                capacity.getSlotCapacityId(),
                capacity.getPartySize(),
                remaining,
                remaining > 0
        );
    }

}
