package com.chatsocial.dto.chat;

import java.time.Instant;

public record MessageResponse(
        String id,
        String conversationId,
        String senderId,
        String content,
        String clientMessageId,
        Instant createdAt,
        boolean readByRecipient
) {}
