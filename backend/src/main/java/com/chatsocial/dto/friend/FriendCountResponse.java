package com.chatsocial.dto.friend;

public record FriendCountResponse(
        String userId,
        long friendCount
) {}
