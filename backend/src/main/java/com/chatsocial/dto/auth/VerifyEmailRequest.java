package com.chatsocial.dto.auth;

import jakarta.validation.constraints.*;
import java.util.Locale;

public record VerifyEmailRequest(@NotBlank @Email @Size(max = 255) String email,
                                 @NotBlank @Pattern(regexp = "[0-9]{6}", message = "must contain exactly 6 digits") String otp) {
    public VerifyEmailRequest {
        email = email == null ? null : email.trim().toLowerCase(Locale.ROOT);
    }

    @Override
    public String toString() { return "VerifyEmailRequest[redacted]"; }
}
