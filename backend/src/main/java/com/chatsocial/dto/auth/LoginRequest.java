package com.chatsocial.dto.auth;

import jakarta.validation.constraints.*;
import java.util.Locale;

public record LoginRequest(
        @NotBlank @Email @Size(max = 255) String email,
        @NotBlank @Size(max = 72) String password) {
    public LoginRequest {
        email = email == null ? null : email.trim().toLowerCase(Locale.ROOT);
    }
}
