package com.chatsocial.dto.auth;

import tools.jackson.databind.annotation.JsonDeserialize;
import com.chatsocial.serialization.NormalizedIdentifierDeserializer;
import com.chatsocial.serialization.TrimmedStringDeserializer;
import jakarta.validation.constraints.*;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class RegisterRequest {
    @NotBlank @Email @Size(max = 255)
    @JsonDeserialize(using = NormalizedIdentifierDeserializer.class)
    private String email;
    @NotBlank @Pattern(regexp = "[a-z0-9_]{3,50}",
            message = "must contain 3-50 lowercase letters, digits or underscores")
    @JsonDeserialize(using = NormalizedIdentifierDeserializer.class)
    private String username;
    @NotBlank @Size(min = 8, max = 72)
    private String password;
    @NotBlank @Size(max = 100)
    @JsonDeserialize(using = TrimmedStringDeserializer.class)
    private String fullName;
}
