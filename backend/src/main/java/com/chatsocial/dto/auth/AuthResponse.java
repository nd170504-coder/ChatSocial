package com.chatsocial.dto.auth;

import com.chatsocial.dto.user.UserResponse;
import lombok.Getter;
import lombok.AllArgsConstructor;

@Getter
@AllArgsConstructor
public class AuthResponse {
    private String accessToken;
    private String tokenType;
    private long expiresIn;
    private String refreshToken;
    private long refreshExpiresIn;
    private String sessionId;
    private UserResponse user;
}
