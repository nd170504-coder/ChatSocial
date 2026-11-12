package com.chatsocial.dto.chat;

public record TypingRequest(
        String conversationId,
        Boolean typing
) {}
