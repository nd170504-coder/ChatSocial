package com.chatsocial.dto.block;

import java.time.Instant;
public record BlockedUserResponse(String userId, String username, String fullName, String avatarUrl, Instant blockedAt) {}
