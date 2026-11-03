package com.chatsocial.config;

import jakarta.validation.constraints.*;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties(prefix = "app.jwt")
@Getter
@Setter
public class JwtProperties {
    @NotBlank
    private String secret;

    @NotBlank
    private String issuer;

    @Min(1)
    @Max(86400)
    private long accessTokenSeconds;

    @Min(60)
    @Max(31536000)
    private long refreshTokenSeconds;
}
