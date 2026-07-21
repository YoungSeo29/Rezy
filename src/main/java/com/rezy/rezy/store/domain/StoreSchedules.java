package com.rezy.rezy.store.domain;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalTime;
import java.util.UUID;

@Entity
@Table(name = "store_schedules")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class StoreSchedules {

    @Id
    @Column(name = "schedule_id", columnDefinition = "VARCHAR(36)")
    private String scheduleId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "store_id")
    private Store store;

    @Column(nullable = false)
    private int capacity;

    @Column(nullable = false)
    private int interval;

    @Column(name = "day_of_week", nullable = false)
    private String dayOfWeek;

    @Column(name = "open_time", nullable = false)
    private LocalTime openTime;

    @Column(name = "close_time", nullable = false)
    private LocalTime closeTime;

    @PrePersist
    public void prePersist() {
        if (scheduleId == null) {
            scheduleId = UUID.randomUUID().toString();
        }
    }

}
