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

    public static SlotCapacityResponse from(SlotCapacity capacity) {
        return new SlotCapacityResponse(
                capacity.getSlotCapacityId(),
                capacity.getPartySize(),
                capacity.getRemainingTeams(),
                capacity.getRemainingTeams() > 0
        );
    }

}
