package com.chatsocial.dto.auth;

import jakarta.validation.constraints.*;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class ChangePasswordRequest {
    @NotBlank @Size(max = 72)
    private String currentPassword;
    @NotBlank @Size(min = 8, max = 72)
    private String newPassword;
    @NotBlank @Size(min = 8, max = 72)
    private String confirmPassword;
}
