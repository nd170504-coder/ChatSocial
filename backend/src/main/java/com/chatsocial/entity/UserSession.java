package com.chatsocial.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.Instant;

@Entity
@Table(name = "user_sessions", schema = "chatsocial", indexes = {
        @Index(name = "idx_user_sessions_user", columnList = "user_id"),
        @Index(name = "idx_user_sessions_expires", columnList = "expires_at")
})
@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class UserSession {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "session_id")
    @Setter(AccessLevel.NONE)
    private String id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false, updatable = false)
    private User user;

    @Column(name = "user_agent", length = 512)
    private String userAgent;

    @Column(name = "created_at", nullable = false, updatable = false, columnDefinition = "timestamp with time zone")
    private Instant createdAt;

    @Column(name = "expires_at", nullable = false, columnDefinition = "timestamp with time zone")
    private Instant expiresAt;

    @Column(name = "revoked_at", columnDefinition = "timestamp with time zone")
    private Instant revokedAt;

    @Column(name = "last_used_at", columnDefinition = "timestamp with time zone")
    private Instant lastUsedAt;

    public UserSession(User user, String userAgent, Instant createdAt, Instant expiresAt) {
        this.user = user;
        this.userAgent = userAgent;
        this.createdAt = createdAt;
        this.expiresAt = expiresAt;
    }
}
