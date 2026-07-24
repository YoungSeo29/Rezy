package com.rezy.rezy.reservation.domain;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Entity
@Table(name = "slot_capacities",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_slot_party_size",
                columnNames = {"slot_id", "party_size"}
        ))
@Getter
@NoArgsConstructor (access = AccessLevel.PROTECTED)
public class SlotCapacity {

    @Id
    @Column(name = "slot_capacity_id", columnDefinition = "VARCHAR(36)")
    private String slotCapacityId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "slot_id", nullable = false)
    private ReservationSlot slot;

    @Column(name = "party_size", nullable = false)
    private int partySize;  // 예약 인원 수

    @Column(name = "total_teams", nullable = false)
    private int totalTeams;  // 최초 정원

    @Column(name = "remaining_teams", nullable = false)
    private int remainingTeams;  // 남은 팀 수

    @PrePersist
    public void prePersist() {
        if(slotCapacityId == null) {
            slotCapacityId = UUID.randomUUID().toString();
        }
    }

    // 버킷 생성 - 남은 팀 수를 정원과 동일하게 초기화 한 뒤 반환
    static SlotCapacity create(ReservationSlot slot, int partySize, int totalTeams) {
        SlotCapacity capacity = new SlotCapacity();
        capacity.slot = slot;
        capacity.partySize = partySize;
        capacity.totalTeams = totalTeams;
        capacity.remainingTeams = totalTeams;

        return capacity;
    }

    // 예약 1건만큼 잔여 팀 수 차감
    public void decreaseRemaining() {
        if(this.remainingTeams <= 0) {
            throw new IllegalStateException("해당 시간대의 예약 가능한 자리가 없습니다.");
        }
        this.remainingTeams -= 1;
    }


}
