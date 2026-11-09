package com.chatsocial.event;

import com.chatsocial.dto.chat.ChatEvent;

public record ChatDelivery(
        String recipientId,
        ChatEvent payload
) {}
