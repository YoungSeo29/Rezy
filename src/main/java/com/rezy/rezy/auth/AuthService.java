package com.rezy.rezy.auth;

import com.rezy.rezy.auth.dto.LoginRequest;
import com.rezy.rezy.auth.dto.SignUpRequest;
import com.rezy.rezy.auth.dto.TokenResponse;
import com.rezy.rezy.global.jwt.JwtTokenProvider;
import com.rezy.rezy.user.UserRepository;
import com.rezy.rezy.user.domain.AuthProvider;
import com.rezy.rezy.user.domain.User;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenProvider jwtTokenProvider;

    @Transactional
    public TokenResponse signup(SignUpRequest req) {
        if(userRepository.existsByEmailAndProvider(req.getEmail(), AuthProvider.LOCAL)) {
            throw new IllegalArgumentException("이미 가입된 이메일입니다.");
        }
        User user = User.createLocal(req.getEmail(), passwordEncoder.encode(req.getPassword()));
        userRepository.save(user);
        String token = jwtTokenProvider.createAccessToken(user.getUserId(), user.getRole().name());
        return TokenResponse.of(token, true);
    }

    @Transactional(readOnly = true)
    public TokenResponse login(LoginRequest req) {
        User user = userRepository.findByEmailAndProvider(req.getEmail(), AuthProvider.LOCAL).
                orElseThrow(() -> new IllegalArgumentException("이메일 또는 비밀번호가 올바르지 않습니다."));
    if (!passwordEncoder.matches(req.getPassword(), user.getPassword())) {
        throw new IllegalArgumentException("이메일 또는 비밀번호가 올바르지 않습니다.");
    }
    String token = jwtTokenProvider.createAccessToken(user.getUserId(), user.getRole().name());

    return TokenResponse.of(token, user.getNickname() == null);
    }
}
