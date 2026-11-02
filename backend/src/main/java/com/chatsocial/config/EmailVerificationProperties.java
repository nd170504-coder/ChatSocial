package com.chatsocial.config;

import jakarta.validation.constraints.*;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties(prefix = "app.email-verification")
public record EmailVerificationProperties(@NotBlank String secret,
        @Min(60) @Max(1800) long ttlSeconds, @Min(1) @Max(3600) long resendCooldownSeconds,
        @Min(1) @Max(10) int maxAttempts, @Min(1) @Max(20) int maxSendsPerHour) {
    @Override
    public String toString() { return "EmailVerificationProperties[secret redacted]"; }
}
