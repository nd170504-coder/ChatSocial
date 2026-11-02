package com.chatsocial.dto.auth;

import com.chatsocial.dto.user.UserResponse;

public record AuthResponse(String accessToken, String tokenType, long expiresIn, UserResponse user) {}
