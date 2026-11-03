package com.chatsocial.service;

import com.chatsocial.config.JwtProperties;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.*;
import org.springframework.stereotype.Service;
import java.time.Instant;

@Service
public class JwtService {
    private final JwtEncoder encoder;
    private final JwtProperties properties;

    public JwtService(JwtEncoder encoder, JwtProperties properties) {
        this.encoder = encoder;
        this.properties = properties;
    }

    public Jwt createAccessToken(String userId, String sessionId, Instant sessionExpiresAt) {
        Instant now = Instant.now().truncatedTo(java.time.temporal.ChronoUnit.SECONDS);
        Instant expiresAt = now.plusSeconds(properties.getAccessTokenSeconds());
        if (sessionExpiresAt.isBefore(expiresAt)) expiresAt = sessionExpiresAt;
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer(properties.getIssuer())
                .subject(userId)
                .id(java.util.UUID.randomUUID().toString())
                .claim("sid", sessionId)
                .issuedAt(now)
                .expiresAt(expiresAt)
                .build();
        return encoder.encode(JwtEncoderParameters.from(JwsHeader.with(MacAlgorithm.HS256).build(), claims));
    }
}
