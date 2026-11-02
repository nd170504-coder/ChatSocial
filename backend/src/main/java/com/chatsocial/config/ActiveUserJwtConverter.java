package com.chatsocial.config;

import com.chatsocial.enums.UserStatus;
import com.chatsocial.repository.UserSessionRepository;
import org.springframework.core.convert.converter.Converter;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.InvalidBearerTokenException;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;
import java.util.List;
import java.time.Instant;

@Component
public class ActiveUserJwtConverter implements Converter<Jwt, AbstractAuthenticationToken> {
    private final UserSessionRepository sessions;

    public ActiveUserJwtConverter(UserSessionRepository sessions) {
        this.sessions = sessions;
    }

    @Override
    public AbstractAuthenticationToken convert(Jwt jwt) {
        Object sessionClaim = jwt.getClaims().get("sid");
        if (!(sessionClaim instanceof String sessionId) || sessionId.isBlank()
                || !sessions.isActive(sessionId, jwt.getSubject(), Instant.now(), UserStatus.ACTIVE)) {
            throw new InvalidBearerTokenException("Session or user is unavailable");
        }
        return new JwtAuthenticationToken(jwt, List.of(), jwt.getSubject());
    }
}
