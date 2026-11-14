package com.chatsocial.config;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Getter
@Setter
@Validated
@ConfigurationProperties(prefix = "app.brevo")
public class BrevoProperties {
    @NotBlank
    private String apiKey;
    @NotBlank
    @Email
    private String senderEmail;
    @NotBlank
    private String senderName;
}
