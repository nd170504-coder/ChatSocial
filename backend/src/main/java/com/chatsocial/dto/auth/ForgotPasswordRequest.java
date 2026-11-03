package com.chatsocial.dto.auth;

import tools.jackson.databind.annotation.JsonDeserialize;
import com.chatsocial.serialization.NormalizedIdentifierDeserializer;
import jakarta.validation.constraints.*;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class ForgotPasswordRequest {
    @NotBlank
    @Email
    @Size(max = 255)
    @JsonDeserialize(using = NormalizedIdentifierDeserializer.class)
    private String email;
}
