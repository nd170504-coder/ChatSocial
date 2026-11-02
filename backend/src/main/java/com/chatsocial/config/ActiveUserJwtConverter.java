package com.chatsocial.config;

import com.chatsocial.enums.UserStatus;
import com.chatsocial.repository.UserRepository;
import org.springframework.core.convert.converter.Converter;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.InvalidBearerTokenException;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;
import java.util.List;

@Component
public class ActiveUserJwtConverter implements Converter<Jwt, AbstractAuthenticationToken> {
    private final UserRepository users;

    public ActiveUserJwtConverter(UserRepository users) {
        this.users = users;
    }

    @Override
    public AbstractAuthenticationToken convert(Jwt jwt) {
        // Re-check status on every request so disabling a user also blocks existing tokens.
        users.findById(jwt.getSubject()).filter(user -> user.getStatus() == UserStatus.ACTIVE)
                .orElseThrow(() -> new InvalidBearerTokenException("User is unavailable"));
        return new JwtAuthenticationToken(jwt, List.of(), jwt.getSubject());
    }
}
