package com.chatsocial.dto.chat;

import java.time.Instant;

public record ConversationResponse(
        String id,
        OtherUserResponse otherUser,
        MessageResponse lastMessage,
        long unreadCount,
        boolean canSend,
        Instant updatedAt
) {}
