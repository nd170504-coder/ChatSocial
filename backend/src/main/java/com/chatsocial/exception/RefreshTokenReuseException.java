package com.chatsocial.exception;

import org.springframework.http.HttpStatus;

public class RefreshTokenReuseException extends ApiException {
    public RefreshTokenReuseException() {
        super(HttpStatus.UNAUTHORIZED, "Refresh token was already used; please log in again");
    }
}
