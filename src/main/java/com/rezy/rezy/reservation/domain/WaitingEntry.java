package com.rezy.rezy.reservation.domain;

import com.rezy.rezy.user.domain.User;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.UUID;

// 취소 대기 줄의 번호표 1장
// Reservation 에 안 얹은 이유 : (user_id, active_date) 유니크 때문에 "같은 날 대기 중" 을 예약 행으로 표현할 수 없다
@Entity
@Table(name = "waiting_entry",
        indexes = @Index(name = "idx_waiting_capacity_status_created",
                columnList = "slot_capacity_id, status, created_at"))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class WaitingEntry {

    // 자리를 넘겨받은 뒤 확정까지 주어지는 시간 - 5분
    public static final Duration OFFER_TTL = Duration.ofMinutes(5);

    @Id
    @Column(name = "waiting_id", columnDefinition = "VARCHAR(36)")
    private String waitingId;

    // 줄은 버킷 단위 = "10월 1일 17시의 4인석" 줄
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "slot_capacity_id", nullable = false)
    private SlotCapacity capacity;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(nullable = false)
    @Enumerated(EnumType.STRING)
    private WaitingStatus status;

    // 순번 기준. position 컬럼을 두면 중간에 빠질 때마다 뒷사람을 전부 고쳐야 한다
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    // OFFERED 일 때만 채움. 5분의 정답은 이 컬럼이고, 배치는 집행만 한다
    @Column(name = "offer_expires_at")
    private LocalDateTime offerExpiresAt;

    @PrePersist
    public void prePersist() {
        if (waitingId == null) {
            waitingId = UUID.randomUUID().toString();
        }
    }

    public static WaitingEntry create(User user, SlotCapacity capacity) {
        WaitingEntry entry = new WaitingEntry();
        entry.user = user;
        entry.capacity = capacity;
        entry.status = WaitingStatus.WAITING;
        entry.createdAt = LocalDateTime.now();
        return entry;
    }

    // 비워진 자리를 이 번호표에 넘긴다. 호출 측이 버킷 행 락을 쥐고 있어야 한다
    public void offer(LocalDateTime now) {
        if (this.status != WaitingStatus.WAITING) {
            throw new IllegalStateException("대기 중인 순번만 자리를 받을 수 있습니다.");
        }
        this.status = WaitingStatus.OFFERED;
        this.offerExpiresAt = now.plus(OFFER_TTL);
    }
}
