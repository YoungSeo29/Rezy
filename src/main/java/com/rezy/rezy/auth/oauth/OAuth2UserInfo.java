package com.rezy.rezy.auth.oauth;

public interface OAuth2UserInfo {
    String getProviderId();
    String getEmail();
    String getNickname();
}
