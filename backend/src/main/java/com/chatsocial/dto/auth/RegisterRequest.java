package com.chatsocial.dto.auth;

import com.fasterxml.jackson.annotation.JsonIgnore;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.*;
import java.nio.charset.StandardCharsets;
import java.util.Locale;

public record RegisterRequest(
        @NotBlank @Email @Size(max = 255) String email,
        @NotBlank @Pattern(regexp = "[a-z0-9_]{3,50}",
                message = "must contain 3-50 lowercase letters, digits or underscores") String username,
        @NotBlank @Size(min = 8, max = 72) String password,
        @NotBlank @Size(max = 100) String fullName) {

    public RegisterRequest {
        email = email == null ? null : email.trim().toLowerCase(Locale.ROOT);
        username = username == null ? null : username.trim().toLowerCase(Locale.ROOT);
        fullName = fullName == null ? null : fullName.trim();
    }

    // BCrypt limits passwords by UTF-8 bytes, not Java character count.
    @JsonIgnore
    @Schema(hidden = true)
    @AssertTrue(message = "password must not exceed 72 UTF-8 bytes")
    public boolean isPasswordWithinByteLimit() {
        return password == null || password.getBytes(StandardCharsets.UTF_8).length <= 72;
    }
}
