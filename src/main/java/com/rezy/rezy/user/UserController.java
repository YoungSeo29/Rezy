package com.rezy.rezy.user;

import com.rezy.rezy.auth.dto.ProfileRequest;
import com.rezy.rezy.user.dto.MyProfileResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/users")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;

    // 닉네임, 역할 넣어서 프로필 완성하기
    @PatchMapping("/me/profile")
    public ResponseEntity<Void> completeProfile(
            Authentication authentication,
            @Valid @RequestBody ProfileRequest req
            ) {
        userService.completeProfile(authentication.getName(), req.getNickname(), req.getRole());

        return ResponseEntity.noContent().build();
    }

    // 내 정보 조회
    @GetMapping("/me")
    public ResponseEntity<MyProfileResponse> getMyProfile(Authentication authentication) {
        return ResponseEntity.ok(userService.getMyProfile(authentication.getName()));
    }
}
