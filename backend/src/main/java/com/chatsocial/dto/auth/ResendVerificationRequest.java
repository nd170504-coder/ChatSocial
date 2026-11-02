package com.chatsocial.dto.auth;

import jakarta.validation.constraints.*;
import java.util.Locale;

public record ResendVerificationRequest(@NotBlank @Email @Size(max = 255) String email) {
    public ResendVerificationRequest {
        email = email == null ? null : email.trim().toLowerCase(Locale.ROOT);
    }
}
