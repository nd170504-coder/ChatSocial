package com.chatsocial.config;

import jakarta.validation.constraints.*;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties(prefix = "app.email-verification")
@Getter
@Setter
public class EmailVerificationProperties {
    @NotBlank
    private String secret;

    @Min(60)
    @Max(1800)
    private long ttlSeconds;

    @Min(1)
    @Max(3600)
    private long resendCooldownSeconds;

    @Min(1)
    @Max(10)
    private int maxAttempts;

    @Min(1)
    @Max(20)
    private int maxSendsPerHour;
}
