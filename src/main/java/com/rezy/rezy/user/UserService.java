package com.rezy.rezy.user;

import com.rezy.rezy.global.exception.NotFoundException;
import com.rezy.rezy.user.domain.User;
import com.rezy.rezy.user.domain.UserRole;
import com.rezy.rezy.user.dto.MyProfileResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class UserService {

    private final UserRepository userRepository;

    @Transactional
    public void completeProfile(String userId, String nickname, UserRole role) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new NotFoundException("사용자를 찾을 수 없습니다."));

        user.completeProfile(nickname, role);

        try {
            userRepository.saveAndFlush(user);
        } catch (DataIntegrityViolationException e) {
            throw new IllegalStateException("이미 사용 중인 닉네임입니다.");
        }
    }

    // 입력 중 중복 확인 버튼용 - 이미 쓰는 닉네임인지만 알려준다
    @Transactional(readOnly = true)
    public boolean isNicknameTaken(String nickname) {
        return userRepository.existsByNickname(nickname);
    }

    @Transactional (readOnly = true)
    public MyProfileResponse getMyProfile(String userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new NotFoundException("사용자를 찾을 수 없습니다."));

        return MyProfileResponse.from(user);
    }
}
