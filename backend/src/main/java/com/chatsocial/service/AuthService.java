package com.chatsocial.service;

import com.chatsocial.dto.auth.*;
import com.chatsocial.dto.user.UserResponse;
import com.chatsocial.entity.*;
import com.chatsocial.enums.UserStatus;
import com.chatsocial.exception.ApiException;
import com.chatsocial.repository.*;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;

@Service
public class AuthService {
    private final UserRepository users;
    private final UserProfileRepository profiles;
    private final PasswordEncoder passwords;
    private final JwtService tokens;
    private final UserSessionService sessions;
    private final String dummyPasswordHash;
    private final EmailVerificationService verification;
    private final TransactionTemplate transactions;

    public AuthService(UserRepository users, UserProfileRepository profiles, PasswordEncoder passwords,
                       JwtService tokens, UserSessionService sessions, EmailVerificationService verification,
                       PlatformTransactionManager transactionManager) {
        this.users = users;
        this.profiles = profiles;
        this.passwords = passwords;
        this.tokens = tokens;
        this.sessions = sessions;
        this.verification = verification;
        this.transactions = new TransactionTemplate(transactionManager);
        this.dummyPasswordHash = passwords.encode("dummy-password-for-timing");
    }

    public UserResponse register(RegisterRequest request) {
        Registration result = java.util.Objects.requireNonNull(transactions.execute(status -> {
            if (users.existsByEmail(request.getEmail()) || users.existsByUsername(request.getUsername())) {
                throw new ApiException(HttpStatus.CONFLICT, "Email or username already exists");
            }
            String passwordHash;
            try {
                passwordHash = passwords.encode(request.getPassword());
            } catch (IllegalArgumentException exception) {
                throw new ApiException(HttpStatus.BAD_REQUEST, "Password exceeds BCrypt's supported byte length");
            }
            User user = users.saveAndFlush(new User(null, request.getEmail(), passwordHash,
                    request.getUsername(), UserStatus.PENDING_VERIFICATION, null, null));
            UserProfile profile = profiles.saveAndFlush(new UserProfile(null, user, request.getFullName(),
                    null, null, null, null, null));
            return new Registration(UserResponse.from(user, profile), verification.issueInitial(user));
        }));
        verification.deliver(result.code());
        return result.user();
    }

    private record Registration(UserResponse user, EmailVerificationService.IssuedCode code) {}

    @Transactional
    public AuthResponse login(LoginRequest request, String userAgent) {
        User user = users.findForUpdateByEmail(request.getEmail()).orElse(null);
        String hash = user == null ? dummyPasswordHash : user.getPasswordHash();
        // Avoid BCrypt's byte limit exception for multibyte input.
        boolean matches = request.getPassword().getBytes(StandardCharsets.UTF_8).length <= 72
                && passwords.matches(request.getPassword(), hash);
        if (!matches || user == null) {
            throw new ApiException(HttpStatus.UNAUTHORIZED, "Invalid email or password");
        }
        if (user.getStatus() != UserStatus.ACTIVE) {
            throw new ApiException(HttpStatus.FORBIDDEN, user.getStatus() == UserStatus.PENDING_VERIFICATION
                    ? "Email verification is required" : "Account is not active");
        }
        return authResponse(sessions.create(user, userAgent));
    }

    @Transactional(noRollbackFor = com.chatsocial.exception.RefreshTokenReuseException.class)
    public AuthResponse refresh(RefreshTokenRequest request) {
        return authResponse(sessions.refresh(request.getRefreshToken()));
    }

    private AuthResponse authResponse(UserSessionService.SessionTokens sessionTokens) {
        UserSession session = sessionTokens.session();
        User user = session.getUser();
        var accessToken = tokens.createAccessToken(user.getId(), session.getId(), session.getExpiresAt());
        return new AuthResponse(accessToken.getTokenValue(), "Bearer",
                Duration.between(accessToken.getIssuedAt(), accessToken.getExpiresAt()).getSeconds(),
                sessionTokens.refreshToken(), Math.max(0, Duration.between(Instant.now(), session.getExpiresAt()).getSeconds()),
                session.getId(), userResponse(user));
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
