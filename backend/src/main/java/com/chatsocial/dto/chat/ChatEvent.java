package com.chatsocial.dto.chat;

public record ChatEvent(
        String type,
        String conversationId,
        MessageResponse message,
        ReadResponse read
) {}
