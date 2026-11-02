package com.chatsocial.utils;

import com.chatsocial.exception.ApiException;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

public final class SecurityUtils {
    private SecurityUtils() {
    }

    /** Returns the subject of the JWT already authenticated for the current request. */
    public static String getCurrentUserId() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (!(authentication instanceof JwtAuthenticationToken jwtAuthentication)
                || !jwtAuthentication.isAuthenticated()) {
            throw new ApiException(HttpStatus.UNAUTHORIZED, "JWT authentication is required");
        }

        String userId = jwtAuthentication.getToken().getSubject();
        if (userId == null || userId.isBlank()) {
            throw new ApiException(HttpStatus.UNAUTHORIZED, "Token subject is missing");
        }
        return userId;
    }
}
