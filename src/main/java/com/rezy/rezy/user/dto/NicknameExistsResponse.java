package com.rezy.rezy.user.dto;

import lombok.Getter;

// 닉네임 중복 확인 응답 - exists=true 면 이미 누가 쓰는 닉네임
@Getter
public class NicknameExistsResponse {

    private final boolean exists;

    public NicknameExistsResponse(boolean exists) {
        this.exists = exists;
    }
}
