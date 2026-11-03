package com.chatsocial.dto.user;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class AccountActionRequest {

    @NotBlank
    @Size(max = 72)
    private String currentPassword;

}
