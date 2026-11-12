package com.chatsocial.dto.chat;

public record TypingEvent(
        String conversationId,
        String userId,
        boolean typing
) {}
