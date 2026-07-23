package com.rezy.rezy.auth.dto;

import com.rezy.rezy.user.domain.UserRole;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
public class ProfileRequest {
    @NotBlank
    @Size(max = 30)
    private String nickname;

    @NotNull
    private UserRole role;
}
