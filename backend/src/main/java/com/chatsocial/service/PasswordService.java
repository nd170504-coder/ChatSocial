package com.chatsocial.service;

import com.chatsocial.config.EmailVerificationProperties;
import com.chatsocial.dto.auth.*;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import com.chatsocial.entity.*;
import com.chatsocial.enums.UserStatus;
import com.chatsocial.exception.*;
import com.chatsocial.repository.*;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.*;
import org.springframework.transaction.support.TransactionTemplate;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.time.Instant;
import java.util.*;

@Service
public class PasswordService {
    private final UserRepository users;
    private final PasswordEncoder passwords;
    private final UserSessionService sessions;
    private final PasswordResetRepository resets;
    private final EmailVerificationProperties properties;
    private final EmailService emails;
    private final TransactionTemplate transactions;
    private final SecretKeySpec key;
    private final SecureRandom random = new SecureRandom();

    public PasswordService(UserRepository users, PasswordEncoder passwords, UserSessionService sessions,
            PasswordResetRepository resets, EmailVerificationProperties properties,
            EmailService emails, PlatformTransactionManager transactionManager) {
        this.users = users;
        this.passwords = passwords;
        this.sessions = sessions;
        this.resets = resets;
        this.properties = properties;
        this.emails = emails;
        this.transactions = new TransactionTemplate(transactionManager);
        byte[] secret = Base64.getDecoder().decode(properties.getSecret());
        if (secret.length < 32) throw new IllegalArgumentException("Email OTP secret requires at least 32 random bytes in Base64");
        this.key = new SecretKeySpec(secret, "HmacSHA256");
    }

    public record IssuedCode(String email, String otp) {
        @Override
        public String toString() { return "IssuedCode[redacted]"; }
    }

    public void forgotPassword(ForgotPasswordRequest request) {
        IssuedCode code = transactions.execute(status -> {
            User user = users.findForUpdateByEmail(request.getEmail()).orElse(null);
            if (user == null || user.getStatus() != UserStatus.ACTIVE) return null;
            PasswordReset reset = resets.findByUserId(user.getId()).orElse(null);
            if (reset == null) return issue(new PasswordReset(user), Instant.now());
            Instant now = Instant.now();
            if (reset.getLastSentAt().plusSeconds(properties.getResendCooldownSeconds()).isAfter(now)) return null;
            if (reset.getSendWindowStartedAt().plusSeconds(3600).isAfter(now)
                    && reset.getSendCount() >= properties.getMaxSendsPerHour()) return null;
            return issue(reset, now);
        });
        if (code != null) emails.sendPasswordResetEmail(code.email(), code.otp(), properties.getTtlSeconds());
    }

    @Transactional(noRollbackFor = InvalidVerificationCodeException.class)
    public void resetPassword(ResetPasswordRequest request) {
        User user = users.findForUpdateByEmail(request.getEmail()).orElseThrow(this::invalidCode);
        if (user.getStatus() != UserStatus.ACTIVE) throw invalidCode();
        PasswordReset reset = resets.findByUserId(user.getId()).orElseThrow(this::invalidCode);
        Instant now = Instant.now();
        if (reset.getConsumedAt() != null || !reset.getExpiresAt().isAfter(now)) throw invalidCode();
        if (reset.getFailedAttempts() >= properties.getMaxAttempts()) {
            throw new InvalidVerificationCodeException(true);
        }
        if (!MessageDigest.isEqual(HexFormat.of().parseHex(reset.getCodeHash()),
                HexFormat.of().parseHex(hash(user.getId(), request.getOtp())))) {
            reset.setFailedAttempts(reset.getFailedAttempts() + 1);
            resets.flush();
            throw new InvalidVerificationCodeException(reset.getFailedAttempts() >= properties.getMaxAttempts());
        }
        updatePassword(user, request.getNewPassword(), request.getConfirmPassword());
        reset.setConsumedAt(now);
    }

    @Transactional
    public void changePassword(String userId, ChangePasswordRequest request) {
        User user = users.findForUpdateById(userId)
                .filter(value -> value.getStatus() == UserStatus.ACTIVE)
                .orElseThrow(() -> new ApiException(HttpStatus.UNAUTHORIZED, "User is unavailable"));
        if (request.getCurrentPassword().getBytes(StandardCharsets.UTF_8).length > 72
                || !passwords.matches(request.getCurrentPassword(), user.getPasswordHash())) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Current password is incorrect");
        }
        updatePassword(user, request.getNewPassword(), request.getConfirmPassword());
        // An outstanding reset code must not survive a password change.
        resets.findByUserId(userId).ifPresent(code -> code.setConsumedAt(Instant.now()));
    }

    private void updatePassword(User user, String password, String confirmation) {
        if (!password.equals(confirmation)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Password confirmation does not match");
        }
        if (password.getBytes(StandardCharsets.UTF_8).length > 72) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Password exceeds BCrypt's supported byte length");
        }
        if (passwords.matches(password, user.getPasswordHash())) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "New password must differ from current password");
        }
        user.setPasswordHash(passwords.encode(password));
        sessions.revokeAll(user.getId());
        String email = user.getEmail();
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                emails.sendPasswordChangedEmail(email);
            }
        });
    }

    private IssuedCode issue(PasswordReset reset, Instant now) {
        String otp;
        String hash;
        do {
            otp = String.format(Locale.ROOT, "%06d", random.nextInt(1_000_000));
            hash = hash(reset.getUser().getId(), otp);
        } while (hash.equals(reset.getCodeHash()));
        if (reset.getSendWindowStartedAt() == null
                || !reset.getSendWindowStartedAt().plusSeconds(3600).isAfter(now)) {
            reset.setSendWindowStartedAt(now);
            reset.setSendCount(0);
        }
        reset.setCodeHash(hash);
        reset.setExpiresAt(now.plusSeconds(properties.getTtlSeconds()));
        reset.setFailedAttempts(0);
        reset.setConsumedAt(null);
        reset.setLastSentAt(now);
        reset.setSendCount(reset.getSendCount() + 1);
        resets.saveAndFlush(reset);
        return new IssuedCode(reset.getUser().getEmail(), otp);
    }

    private String hash(String userId, String otp) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(key);
            return HexFormat.of().formatHex(mac.doFinal(
                    ("chatsocial:password-reset:" + userId + ":" + otp).getBytes(StandardCharsets.UTF_8)));
        } catch (GeneralSecurityException exception) {
            throw new IllegalStateException("Unable to protect reset code", exception);
        }
    }

    private ApiException invalidCode() {
        return new ApiException(HttpStatus.BAD_REQUEST, "Invalid or expired reset code");
    }
}
