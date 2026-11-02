package com.chatsocial.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.Instant;

@Entity
@Table(name = "email_verifications", schema = "chatsocial",
        uniqueConstraints = @UniqueConstraint(name = "uq_email_verifications_user", columnNames = "user_id"))
@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class EmailVerification {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "verification_id")
    @Setter(AccessLevel.NONE)
    private String id;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false, updatable = false)
    private User user;

    @Column(name = "code_hash", nullable = false, length = 64)
    private String codeHash;

    @Column(name = "expires_at", nullable = false, columnDefinition = "timestamp with time zone")
    private Instant expiresAt;

    @Column(name = "failed_attempts", nullable = false)
    private int failedAttempts;

    @Column(name = "last_sent_at", nullable = false, columnDefinition = "timestamp with time zone")
    private Instant lastSentAt;

    @Column(name = "send_window_started_at", nullable = false, columnDefinition = "timestamp with time zone")
    private Instant sendWindowStartedAt;

    @Column(name = "send_count", nullable = false)
    private int sendCount;

    @Column(name = "consumed_at", columnDefinition = "timestamp with time zone")
    private Instant consumedAt;

    public EmailVerification(User user) {
        this.user = user;
    }
}
