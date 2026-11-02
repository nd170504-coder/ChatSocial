package com.chatsocial.service;

import com.chatsocial.dto.auth.*;
import com.chatsocial.dto.user.UserResponse;
import com.chatsocial.config.JwtProperties;
import com.chatsocial.entity.*;
import com.chatsocial.enums.UserStatus;
import com.chatsocial.exception.ApiException;
import com.chatsocial.repository.*;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.nio.charset.StandardCharsets;

@Service
public class AuthService {
    private final UserRepository users;
    private final UserProfileRepository profiles;
    private final PasswordEncoder passwords;
    private final JwtService tokens;
    private final JwtProperties properties;
    private final String dummyPasswordHash;

    public AuthService(UserRepository users, UserProfileRepository profiles, PasswordEncoder passwords,
                       JwtService tokens, JwtProperties properties) {
        this.users = users;
        this.profiles = profiles;
        this.passwords = passwords;
        this.tokens = tokens;
        this.properties = properties;
        this.dummyPasswordHash = passwords.encode("dummy-password-for-timing");
    }

    @Transactional
    public UserResponse register(RegisterRequest request) {
        if (users.existsByEmail(request.email()) || users.existsByUsername(request.username())) {
            throw new ApiException(HttpStatus.CONFLICT, "Email or username already exists");
        }
        User user = users.saveAndFlush(new User(null, request.email(), passwords.encode(request.password()),
                request.username(), UserStatus.ACTIVE, null, null));
        UserProfile profile = profiles.saveAndFlush(new UserProfile(null, user, request.fullName(),
                null, null, null, null, null));
        return UserResponse.from(user, profile);
    }

    @Transactional(readOnly = true)
    public AuthResponse login(LoginRequest request) {
        User user = users.findByEmail(request.email()).orElse(null);
        String hash = user == null ? dummyPasswordHash : user.getPasswordHash();
        // Avoid BCrypt's byte limit exception for multibyte input.
        boolean matches = request.password().getBytes(StandardCharsets.UTF_8).length <= 72
                && passwords.matches(request.password(), hash);
        if (!matches || user == null) {
            throw new ApiException(HttpStatus.UNAUTHORIZED, "Invalid email or password");
        }
        if (user.getStatus() != UserStatus.ACTIVE) {
            throw new ApiException(HttpStatus.FORBIDDEN, "Account is not active");
        }
        return new AuthResponse(tokens.createAccessToken(user.getId()), "Bearer",
                properties.accessTokenSeconds(), userResponse(user));
    }

    @Transactional(readOnly = true)
    public UserResponse me(String userId) {
        User user = users.findById(userId).filter(value -> value.getStatus() == UserStatus.ACTIVE)
                .orElseThrow(() -> new ApiException(HttpStatus.UNAUTHORIZED, "User is unavailable"));
        return userResponse(user);
    }

    private UserResponse userResponse(User user) {
        UserProfile profile = profiles.findByUserId(user.getId())
                .orElseThrow(() -> new IllegalStateException("User profile is missing"));
        return UserResponse.from(user, profile);
    }
}
