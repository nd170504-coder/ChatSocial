package com.chatsocial.dto.chat;

public record OtherUserResponse(
        String userId,
        String username,
        String fullName,
        String avatarUrl
) {}
