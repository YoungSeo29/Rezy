package com.rezy.rezy.reservation.domain;

import com.rezy.rezy.store.domain.Store;
import com.rezy.rezy.user.domain.User;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "reservations")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Reservation {

    @Id
    @Column(name = "reservation_id", columnDefinition = "VARCHAR(36)")
    private String reservationId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "store_id", nullable = false)
    private Store store;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "slot_id", nullable = false)
    private ReservationSlot slot;

    @Column(name = "reservation_date", nullable = false)
    private LocalDateTime reservationDate;

    @Column(name = "party_size", nullable = false)
    private int partySize;

    @Column(nullable = false)
    @Enumerated(EnumType.STRING)
    private ReservationStatus status;

    @PrePersist
    public void prePersist() {
        if (reservationId == null) {
            reservationId = UUID.randomUUID().toString();
        }
        if (status == null) {
            status = ReservationStatus.PENDING;
        }
    }

}
