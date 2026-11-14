package com.chatsocial.dto.user;

import com.chatsocial.entity.User;
import com.chatsocial.enums.UserRole;
import com.chatsocial.enums.UserStatus;
import java.time.Instant;

public record AdminUserResponse(String id, String username, String email, UserRole role,
                                UserStatus status, Instant createdAt, String moderationReason,
                                Instant moderatedAt) {
    public static AdminUserResponse from(User user) {
        return new AdminUserResponse(user.getId(), user.getUsername(), user.getEmail(), user.getRole(),
                user.getStatus(), user.getCreatedAt(), user.getModerationReason(), user.getModeratedAt());
    }
}
