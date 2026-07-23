package com.rezy.rezy.auth.dto;

import lombok.Getter;

@Getter
public class TokenResponse {

    private final String tokenType;
    private final String accessToken;
    private final boolean needsProfile;

    public TokenResponse(String tokenType, String accessToken, boolean needsProfile) {
        this.tokenType = tokenType;
        this.accessToken = accessToken;
        this.needsProfile = needsProfile;
    }

    public static TokenResponse of(String accessToken, boolean needsProfile) {
        return new TokenResponse("Bearer", accessToken, needsProfile);
    }
}
