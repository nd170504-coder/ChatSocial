package com.chatsocial.dto.presence;

import java.time.Instant;

public record FriendPresenceResponse(
        String userId,
        boolean online,
        Instant lastSeenAt
) {}
