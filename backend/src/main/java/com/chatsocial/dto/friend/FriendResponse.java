package com.chatsocial.dto.friend;

public record FriendResponse(
        String userId,
        String username,
        String fullName,
        String avatarUrl
) {}
