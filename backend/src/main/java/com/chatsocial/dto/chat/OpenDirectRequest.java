package com.chatsocial.dto.chat;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record OpenDirectRequest(
        @NotBlank
        @Size(max = 36)
        String userId
) {}
