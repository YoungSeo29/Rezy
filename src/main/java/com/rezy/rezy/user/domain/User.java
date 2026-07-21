package com.rezy.rezy.user.domain;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.util.UUID;

@Entity
@Table(name = "users")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class User {

    @Id
    @Column(name = "user_id", columnDefinition = "VARCHAR(36)")
    private String userId;

    @Column(nullable = false)
    private String email;

    @Column(nullable = false)
    private String nickname;

    @Column(nullable = false)
    private String role;

    @Column(name = "create_at", nullable = true)
    private LocalDate createAt;

    @PrePersist
    public void prePersist() {
        if (userId == null) {
            userId = UUID.randomUUID().toString();
        }
        if (createAt == null) {
            createAt = LocalDate.now();
        }
    }

}
