package com.rezy.rezy.user.dto;

import com.rezy.rezy.user.domain.User;
import lombok.Getter;

import java.time.LocalDateTime;

@Getter
public class MyProfileResponse {

    private final String userId;
    private final String email;
    private final String nickname;
    private final String role;
    private final String provider;     // LOCAL / KAKAO / NAVER
    private final LocalDateTime createdAt;

    public MyProfileResponse(String userId, String email, String nickname,
                             String role, String provider, LocalDateTime createdAt) {
        this.userId = userId;
        this.email = email;
        this.nickname = nickname;
        this.role = role;
        this.provider = provider;
        this.createdAt = createdAt;
    }

    // User 엔티티를 마이페이지 응답으로 변환 — 비밀번호 등 민감정보는 제외하고 반환
    public static MyProfileResponse from(User user) {
        return new MyProfileResponse(
                user.getUserId(),
                user.getEmail(),
                user.getNickname(),
                user.getRole().name(),
                user.getProvider().name(),
                user.getCreatedAt()
        );
    }

}
