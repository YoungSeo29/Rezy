package com.rezy.rezy.auth.oauth;

import com.rezy.rezy.global.jwt.JwtTokenProvider;
import com.rezy.rezy.user.domain.User;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.Authentication;
import org.springframework.security.web.authentication.SimpleUrlAuthenticationSuccessHandler;
import org.springframework.stereotype.Component;
import org.springframework.web.util.UriComponentsBuilder;

import java.io.IOException;

@Component
public class OAuth2SuccessHandler extends SimpleUrlAuthenticationSuccessHandler {

    private final JwtTokenProvider jwtTokenProvider;
    private final String redirectUri;

    public OAuth2SuccessHandler(JwtTokenProvider jwtTokenProvider,
                                @Value("${app.oauth2.redirect-uri}") String redirectUri) {
        this.jwtTokenProvider = jwtTokenProvider;
        this.redirectUri = redirectUri;
    }

    @Override
    public void onAuthenticationSuccess(HttpServletRequest request, HttpServletResponse response, Authentication authentication) throws IOException {

        User user = ((CustomOAuth2User) authentication.getPrincipal()).getUser();
        String token = jwtTokenProvider.createAccessToken(user.getUserId(), user.getRole().name());

        String url = UriComponentsBuilder.fromUriString(redirectUri)
                .queryParam("token", token)
                .queryParam("needsProfile", user.getNickname() == null)
                .build().toUriString();
        getRedirectStrategy().sendRedirect(request, response, url);

    }
}
