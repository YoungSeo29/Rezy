package com.rezy.rezy.auth.oauth;

import java.util.Map;

public class KakaoUserInfo implements OAuth2UserInfo{

    private final Map<String,Object> attributes;

    public KakaoUserInfo(Map<String,Object> attributes){
        this.attributes = attributes;
    }

    @Override
    public String getProviderId() {
        return String.valueOf(attributes.get("id"));
    }

        @Override
    @SuppressWarnings("unchecked")
    public String getEmail() {
        Map<String,Object> kakaoAccount = (Map<String,Object>) attributes.get("kakao_account");
        return kakaoAccount == null ? null : (String) kakaoAccount.get("email");
    }

    @Override
    @SuppressWarnings("unchecked")
    public String getNickname() {
        Map<String, Object> kakaoAccount = (Map<String, Object>) attributes.get("kakao_account");

        if (kakaoAccount == null)
            return null;

        Map<String, Object> profile = (Map<String, Object>) kakaoAccount.get("profile");
        return profile == null ? null : (String) profile.get("nickname");
    }

}
