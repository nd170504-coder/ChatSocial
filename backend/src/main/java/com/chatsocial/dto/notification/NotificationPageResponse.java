package com.chatsocial.dto.notification;

import java.util.List;

public record NotificationPageResponse(
        List<NotificationResponse> items,
        int page,
        int size,
        boolean hasNext
) {}
