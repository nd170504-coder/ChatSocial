package com.chatsocial.service;

import com.chatsocial.config.EmailVerificationProperties;
import com.chatsocial.dto.auth.*;
import com.chatsocial.dto.user.UserResponse;
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
public class EmailVerificationService {
    private final UserRepository users;
    private final UserProfileRepository profiles;
    private final EmailVerificationRepository verifications;
    private final EmailVerificationProperties properties;
    private final EmailService emails;
    private final TransactionTemplate transactions;
    private final SecretKeySpec key;
    private final SecureRandom random = new SecureRandom();

    public EmailVerificationService(UserRepository users, UserProfileRepository profiles,
            EmailVerificationRepository verifications, EmailVerificationProperties properties,
            EmailService emails, PlatformTransactionManager transactionManager) {
        this.users = users;
        this.profiles = profiles;
        this.verifications = verifications;
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

    @Transactional(propagation = Propagation.MANDATORY)
    public IssuedCode issueInitial(User user) {
        EmailVerification verification = new EmailVerification(user);
        return issue(verification, Instant.now());
    }

    public void deliver(IssuedCode code) {
        emails.sendVerificationEmail(code.email(), code.otp(), properties.getTtlSeconds());
    }

    public void resend(ResendVerificationRequest request) {
        IssuedCode code = transactions.execute(status -> {
            User user = users.findForUpdateByEmail(request.getEmail()).orElse(null);
            if (user == null || user.getStatus() != UserStatus.PENDING_VERIFICATION) return null;
            EmailVerification verification = verifications.findByUserId(user.getId()).orElse(null);
            if (verification == null) return issue(new EmailVerification(user), Instant.now());
            Instant now = Instant.now();
            if (verification.getLastSentAt().plusSeconds(properties.getResendCooldownSeconds()).isAfter(now)) return null;
            if (verification.getSendWindowStartedAt().plusSeconds(3600).isAfter(now)
                    && verification.getSendCount() >= properties.getMaxSendsPerHour()) return null;
            return issue(verification, now);
        });
        // Email is sent only after commit. SMTP failure leaves a recoverable pending account.
        if (code != null) deliver(code);
    }

    @Transactional(noRollbackFor = InvalidVerificationCodeException.class)
    public UserResponse verify(VerifyEmailRequest request) {
        User user = users.findForUpdateByEmail(request.getEmail()).orElseThrow(this::invalidCode);
        if (user.getStatus() != UserStatus.PENDING_VERIFICATION) throw invalidCode();
        EmailVerification verification = verifications.findByUserId(user.getId()).orElseThrow(this::invalidCode);
        Instant now = Instant.now();
        if (verification.getConsumedAt() != null || !verification.getExpiresAt().isAfter(now)) throw invalidCode();
        if (verification.getFailedAttempts() >= properties.getMaxAttempts()) {
            throw new InvalidVerificationCodeException(true);
        }
        if (!MessageDigest.isEqual(HexFormat.of().parseHex(verification.getCodeHash()),
                HexFormat.of().parseHex(hash(user.getId(), request.getOtp())))) {
            verification.setFailedAttempts(verification.getFailedAttempts() + 1);
            verifications.flush();
            throw new InvalidVerificationCodeException(verification.getFailedAttempts() >= properties.getMaxAttempts());
        }
        verification.setConsumedAt(now);
        user.setStatus(UserStatus.ACTIVE);
        var profile = profiles.findByUserId(user.getId()).orElseThrow(() -> new IllegalStateException("User profile is missing"));
        return UserResponse.from(user, profile);
    }

    private IssuedCode issue(EmailVerification verification, Instant now) {
        String otp;
        String hash;
        do {
            otp = String.format(Locale.ROOT, "%06d", random.nextInt(1_000_000));
            hash = hash(verification.getUser().getId(), otp);
        } while (hash.equals(verification.getCodeHash()));
        if (verification.getSendWindowStartedAt() == null
                || !verification.getSendWindowStartedAt().plusSeconds(3600).isAfter(now)) {
            verification.setSendWindowStartedAt(now);
            verification.setSendCount(0);
        }
        verification.setCodeHash(hash);
        verification.setExpiresAt(now.plusSeconds(properties.getTtlSeconds()));
        verification.setFailedAttempts(0);
        verification.setConsumedAt(null);
        verification.setLastSentAt(now);
        verification.setSendCount(verification.getSendCount() + 1);
        verifications.saveAndFlush(verification);
        return new IssuedCode(verification.getUser().getEmail(), otp);
    }

    private String hash(String userId, String otp) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(key);
            return HexFormat.of().formatHex(mac.doFinal(
                    ("chatsocial:email-verification:" + userId + ":" + otp).getBytes(StandardCharsets.UTF_8)));
        } catch (GeneralSecurityException exception) {
            throw new IllegalStateException("Unable to protect verification code", exception);
        }
    }

    private ApiException invalidCode() {
        return new ApiException(HttpStatus.BAD_REQUEST, "Invalid or expired verification code");
    }
}
