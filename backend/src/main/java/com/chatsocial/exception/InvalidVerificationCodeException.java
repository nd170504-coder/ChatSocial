package com.chatsocial.exception;

import org.springframework.http.HttpStatus;

public class InvalidVerificationCodeException extends ApiException {
    public InvalidVerificationCodeException(boolean exhausted) {
        super(exhausted ? HttpStatus.TOO_MANY_REQUESTS : HttpStatus.BAD_REQUEST,
                exhausted ? "Too many verification attempts; request a new code" : "Invalid or expired verification code");
    }
}
