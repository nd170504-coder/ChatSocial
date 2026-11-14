package com.chatsocial.dto.user;

import com.chatsocial.entity.User;
import com.chatsocial.entity.UserProfile;
import com.chatsocial.enums.UserStatus;
import com.chatsocial.enums.UserRole;
import lombok.Getter;
import lombok.AllArgsConstructor;

@Getter
@AllArgsConstructor
public class UserResponse {
    private String id;
    private String email;
    private String username;
    private String fullName;
    private UserStatus status;
    private UserRole role;

    public static UserResponse from(User user, UserProfile profile) {
        return new UserResponse(user.getId(), user.getEmail(), user.getUsername(),
                profile.getFullName(), user.getStatus(), user.getRole());
    }
}
