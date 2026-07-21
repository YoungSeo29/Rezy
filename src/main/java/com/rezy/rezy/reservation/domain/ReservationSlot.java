package com.rezy.rezy.reservation.domain;

import com.rezy.rezy.store.domain.Store;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
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

    @Column(nullable = false)
    private int capacity;

    @Column(nullable = false)
    private int remaining;

    @PrePersist
    public void prePersist() {
        if (slotId == null) {
            slotId = UUID.randomUUID().toString();
        }
    }

}
