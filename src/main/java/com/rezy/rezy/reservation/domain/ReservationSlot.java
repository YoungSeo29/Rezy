package com.rezy.rezy.reservation.domain;

import com.rezy.rezy.store.domain.Store;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "reservation_slots")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ReservationSlot {

    @Id
    @Column(name = "slot_id", columnDefinition = "VARCHAR(36)")
    private String slotId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "store_id", nullable = false)
    private Store store;

    @Column(name = "slot_datetime", nullable = false)
    private LocalDateTime slotDatetime;

    // 한 시간대(슬롯)에 여러 인원수 버킷(2인·4인...)을 가짐. 슬롯 저장 시 함께 저장(cascade)
    @OneToMany(mappedBy = "slot", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<SlotCapacity> capacities = new ArrayList<>();

    @PrePersist
    public void prePersist() {
        if (slotId == null) {
            slotId = UUID.randomUUID().toString();
        }
    }

    // 슬롯 생성 - store와 시간만으로 빈 슬롯을 만들어 반환 (버킷은 addCapacity로 채울 예정)
    public static ReservationSlot create(Store store, LocalDateTime slotDatetime) {
        ReservationSlot slot = new ReservationSlot();
        slot.store = store;
        slot.slotDatetime = slotDatetime;
        return slot;
    }

    // 인원수 버킷 추가
    public void addCapacity(int partySize, int teamCount) {
        this.capacities.add(SlotCapacity.create(this, partySize, teamCount));
    }

}
