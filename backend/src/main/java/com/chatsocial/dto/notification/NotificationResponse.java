package com.chatsocial.dto.notification;

import com.chatsocial.enums.NotificationType;

import java.time.Instant;

public record NotificationResponse(
        String id,
        NotificationType type,
        String actorId,
        String actorName,
        String actorAvatarUrl,
        String postId,
        Instant createdAt,
        Instant readAt
) {}
