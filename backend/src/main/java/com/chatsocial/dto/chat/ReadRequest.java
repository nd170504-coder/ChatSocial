package com.chatsocial.dto.chat;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ReadRequest(
        @NotBlank
        @Size(max = 36)
        String lastReadMessageId
) {}
