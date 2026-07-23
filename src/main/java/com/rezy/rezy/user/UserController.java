package com.rezy.rezy.user;

import com.rezy.rezy.auth.dto.ProfileRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/users")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;

    @PatchMapping("/me/profile")
    public ResponseEntity<Void> completeProfile(
            Authentication authentication,
            @Valid @RequestBody ProfileRequest req
            ) {
        userService.completeProfile(authentication.getName(), req.getNickname(), req.getRole());

        return ResponseEntity.noContent().build();
    }
}
