package com.chatsocial.dto.auth;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class RefreshTokenRequest {
    @NotBlank
    @Pattern(regexp = "[A-Za-z0-9_-]{43}", message = "invalid refresh token format")
    private String refreshToken;
}
