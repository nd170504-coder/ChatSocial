package com.chatsocial.service;

import com.chatsocial.config.JwtProperties;
import com.chatsocial.entity.*;
import com.chatsocial.enums.UserStatus;
import com.chatsocial.exception.*;
import com.chatsocial.repository.*;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.*;

@Service
public class UserSessionService {
    private final UserSessionRepository sessions;
    private final UserRefreshTokenRepository refreshTokens;
    private final JwtProperties properties;
    private final SecureRandom random = new SecureRandom();

    public UserSessionService(UserSessionRepository sessions, UserRefreshTokenRepository refreshTokens,
                              JwtProperties properties) {
        this.sessions = sessions;
        this.refreshTokens = refreshTokens;
        this.properties = properties;
    }

    public record SessionTokens(UserSession session, String refreshToken) {
        @Override
        public String toString() { return "SessionTokens[redacted]"; }
    }

    @Transactional
    public SessionTokens create(User user, String userAgent) {
        Instant now = Instant.now().truncatedTo(ChronoUnit.SECONDS);
        String agent = userAgent == null ? null : userAgent.replaceAll("[\\p{Cntrl}]", "").trim();
        if (agent != null && agent.length() > 512) agent = agent.substring(0, 512);
        UserSession session = sessions.saveAndFlush(new UserSession(user, agent, now,
                now.plusSeconds(properties.refreshTokenSeconds())));
        return new SessionTokens(session, issueRefreshToken(session, now));
    }

    // Replay must commit the revocation even though the request returns HTTP 401.
    @Transactional(noRollbackFor = RefreshTokenReuseException.class)
    public SessionTokens refresh(String rawToken) {
        String hash = hash(rawToken);
        String sessionId = refreshTokens.findSessionIdByTokenHash(hash).orElseThrow(this::invalidToken);
        // Lock the session before loading the token entity so concurrent refreshes cannot both rotate it.
        UserSession session = sessions.findForUpdateById(sessionId).orElseThrow(this::invalidToken);
        Instant now = Instant.now();
        if (session.getRevokedAt() != null || !session.getExpiresAt().isAfter(now)
                || session.getUser().getStatus() != UserStatus.ACTIVE) {
            throw invalidToken();
        }
        UserRefreshToken token = refreshTokens.findByTokenHash(hash).orElseThrow(this::invalidToken);
        if (token.getConsumedAt() != null) {
            session.setRevokedAt(now);
            sessions.flush();
            throw new RefreshTokenReuseException();
        }
        token.setConsumedAt(now);
        session.setLastUsedAt(now);
        return new SessionTokens(session, issueRefreshToken(session, now));
    }

    @Transactional
    public void logout(String userId, String sessionId) {
        UserSession session = sessions.findForUpdateById(sessionId).orElseThrow(this::invalidToken);
        if (!session.getUser().getId().equals(userId)) throw invalidToken();
        if (session.getRevokedAt() == null) session.setRevokedAt(Instant.now());
    }

    private String issueRefreshToken(UserSession session, Instant now) {
        byte[] bytes = new byte[32];
        random.nextBytes(bytes);
        String rawToken = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        refreshTokens.saveAndFlush(new UserRefreshToken(session, hash(rawToken), now));
        return rawToken;
    }

    private String hash(String rawToken) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(rawToken.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }

    private ApiException invalidToken() {
        return new ApiException(HttpStatus.UNAUTHORIZED, "Refresh token or session is invalid or expired");
    }
}
