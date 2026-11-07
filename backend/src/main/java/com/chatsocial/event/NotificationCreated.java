package com.chatsocial.event;

import com.chatsocial.dto.notification.NotificationResponse;

public record NotificationCreated(
        String recipientId,
        NotificationResponse notification
) {}
