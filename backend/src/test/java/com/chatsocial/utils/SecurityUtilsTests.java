package com.chatsocial.utils;

import com.chatsocial.exception.ApiException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class SecurityUtilsTests {
    @AfterEach
    void clearContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void returnsSubjectFromAuthenticatedJwt() {
        SecurityContextHolder.getContext().setAuthentication(jwtAuthentication("user-123"));
        assertEquals("user-123", SecurityUtils.getCurrentUserId());
    }

    @Test
    void rejectsMissingAuthentication() {
        SecurityContextHolder.clearContext();
        assertUnauthorized();
    }

    @Test
    void rejectsOtherAuthenticationTypes() {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken("user-123", null, List.of()));
        assertUnauthorized();
    }

    @Test
    void rejectsUnauthenticatedJwt() {
        JwtAuthenticationToken authentication = jwtAuthentication("user-123");
        authentication.setAuthenticated(false);
        SecurityContextHolder.getContext().setAuthentication(authentication);
        assertUnauthorized();
    }

    @Test
    void rejectsMissingOrBlankSubject() {
        for (String subject : new String[]{null, " "}) {
            SecurityContextHolder.getContext().setAuthentication(jwtAuthentication(subject));
            assertUnauthorized();
        }
    }

    private JwtAuthenticationToken jwtAuthentication(String subject) {
        Jwt jwt = Jwt.withTokenValue("test-token").header("alg", "HS256")
                .claim("iss", "test").subject(subject).build();
        return new JwtAuthenticationToken(jwt, List.of());
    }

    private void assertUnauthorized() {
        ApiException exception = assertThrows(ApiException.class, SecurityUtils::getCurrentUserId);
        assertEquals(HttpStatus.UNAUTHORIZED, exception.getStatus());
    }
}
