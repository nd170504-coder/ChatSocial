package com.chatsocial.dto.chat;

import java.time.Instant;

public record ReadResponse(
        String conversationId,
        String readerId,
        String lastReadMessageId,
        Instant lastReadMessageCreatedAt,
        long unreadCount
) {}
