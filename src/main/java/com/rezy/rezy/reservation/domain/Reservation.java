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

    // 실제 예약한 인원 버킷 slot_capacity_id = "10월 1일 17시의 4인석"
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "slot_capacity_id", nullable = false)
    private SlotCapacity capacity;

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

    public static Reservation create (User user, SlotCapacity capacity) {
        ReservationSlot slot = capacity.getSlot();
        Reservation reservation = new Reservation();

        reservation.user = user;
        reservation.capacity = capacity;
        reservation.slot = slot;
        reservation.store = slot.getStore();
        reservation.reservationDate = slot.getSlotDatetime();
        reservation.partySize = capacity.getPartySize();
        reservation.status = ReservationStatus.CONFIRMED;

        return reservation;
    }

    public void cancel() {
        if(this.status == ReservationStatus.CANCELLED) {
            throw new IllegalStateException("이미 취소된 예약입니다.");
        }
        this.status = ReservationStatus.CANCELLED;
    }

}
