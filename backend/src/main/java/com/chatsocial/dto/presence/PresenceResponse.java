package com.chatsocial.dto.presence;

import java.util.List;

public record PresenceResponse(
        String type,
        List<String> onlineUserIds
) {}
