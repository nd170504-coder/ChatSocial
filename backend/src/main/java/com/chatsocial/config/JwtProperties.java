package com.chatsocial.config;

import jakarta.validation.constraints.*;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties(prefix = "app.jwt")
public record JwtProperties(@NotBlank String secret, @NotBlank String issuer,
                            @Min(1) @Max(86400) long accessTokenSeconds,
                            @Min(60) @Max(31536000) long refreshTokenSeconds) {}
