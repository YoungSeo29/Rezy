package com.rezy.rezy.auth.oauth;

import com.rezy.rezy.user.domain.AuthProvider;

import java.util.Map;

public final class OAuth2UserInfoFactory {

    private OAuth2UserInfoFactory() {}
    public static OAuth2UserInfo get(AuthProvider provider, Map<String, Object> attributes) {

        return switch (provider) {
            case KAKAO -> new KakaoUserInfo(attributes);
            case NAVER -> new NaverUserInfo(attributes);
            default -> throw new IllegalArgumentException("지원하지 않는 소셜: " + provider);
        };

    }
}
