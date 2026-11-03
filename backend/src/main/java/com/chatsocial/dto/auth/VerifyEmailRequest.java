package com.chatsocial.dto.auth;

import tools.jackson.databind.annotation.JsonDeserialize;
import com.chatsocial.serialization.NormalizedIdentifierDeserializer;
import jakarta.validation.constraints.*;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class VerifyEmailRequest {
    @NotBlank
    @Email
    @Size(max = 255)
    @JsonDeserialize(using = NormalizedIdentifierDeserializer.class)
    private String email;

    @NotBlank
    @Pattern(regexp = "[0-9]{6}", message = "must contain exactly 6 digits")
    private String otp;
}
