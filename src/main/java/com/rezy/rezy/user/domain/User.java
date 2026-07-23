package com.rezy.rezy.user.domain;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "users",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_provider_provider_id",
                columnNames = {"provider", "provider_id"}
        ))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class User {

    @Id
    @Column(name = "user_id", columnDefinition = "VARCHAR(36)")
    private String userId;

    @Column(nullable = true)
    private String email;

    @Column(nullable = true)
    private String password;

    @Column(nullable = false)
    @Enumerated(EnumType.STRING)
    private AuthProvider provider;

    @Column(name = "provider_id", nullable = true)
    private String providerId;

    @Column(nullable = true)
    private String nickname;

    @Column(nullable = false)
    @Enumerated(EnumType.STRING)
    private UserRole role;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    public void prePersist() {
        if (userId == null) {
            userId = UUID.randomUUID().toString();
        }
    }

    // 로컬 회원가입 시 호출
    // role은 기본 USER, nickname은 아직 없음
    public static User createLocal(String email, String password) {
        User user = new User();
        user.email = email;
        user.password = password;
        user.role = UserRole.USER;
        user.provider = AuthProvider.LOCAL;
        return user;
    }

    // 소셜 회원가입 시 호출 — role 기본 USER, nickname은 아직 없음
    public static User createOAuth(String email, String providerId, AuthProvider provider) {
        User user = new User();
        user.email = email;
        user.providerId = providerId;
        user.provider = provider;
        user.role = UserRole.USER;
        return user;
    }

    // 프로필 완성 - 닉네임, 역할 결정
    public void completeProfile(String nickname, UserRole role) {
        this.nickname = nickname;
        this.role = role;
    }


}
