package com.rezy.rezy.auth.oauth;

import com.rezy.rezy.user.UserRepository;
import com.rezy.rezy.user.domain.AuthProvider;
import com.rezy.rezy.user.domain.User;
import lombok.RequiredArgsConstructor;
import org.springframework.security.oauth2.client.userinfo.DefaultOAuth2UserService;
import org.springframework.security.oauth2.client.userinfo.OAuth2UserRequest;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class CustomOAuth2UserService extends DefaultOAuth2UserService {

    private final UserRepository userRepository;

    @Override
    @Transactional
    public OAuth2User loadUser (OAuth2UserRequest req) throws OAuth2AuthenticationException {
        OAuth2User oAuth2User = super.loadUser(req);

        String registrationId = req.getClientRegistration().getRegistrationId();
        AuthProvider provider = AuthProvider.valueOf(registrationId.toUpperCase());
        String nameAttrKey = req.getClientRegistration()
                .getProviderDetails().getUserInfoEndpoint().getUserNameAttributeName();

        OAuth2UserInfo info = OAuth2UserInfoFactory.get(provider, oAuth2User.getAttributes());

        User user = userRepository.findByProviderAndProviderId(provider, info.getProviderId())
                .orElseGet(() -> userRepository.save(
                        User.createOAuth(info.getEmail(), info.getProviderId(), provider)));

        return new CustomOAuth2User(user, oAuth2User.getAttributes(), nameAttrKey);
    }
}
