package com.chatsocial.dto.user;

import com.chatsocial.entity.User;
import com.chatsocial.entity.UserProfile;
import com.chatsocial.enums.UserStatus;

public record UserResponse(String id, String email, String username, String fullName, UserStatus status) {
    public static UserResponse from(User user, UserProfile profile) {
        return new UserResponse(user.getId(), user.getEmail(), user.getUsername(),
                profile.getFullName(), user.getStatus());
    }
}
