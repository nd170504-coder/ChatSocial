package com.chatsocial.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.Instant;

@Entity
@Table(name = "user_refresh_tokens", schema = "chatsocial",
        uniqueConstraints = @UniqueConstraint(name = "uq_refresh_token_hash", columnNames = "token_hash"),
        indexes = @Index(name = "idx_refresh_tokens_session", columnList = "session_id"))
@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class UserRefreshToken {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "refresh_token_id")
    @Setter(AccessLevel.NONE)
    private String id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "session_id", nullable = false, updatable = false)
    private UserSession session;

    @Column(name = "token_hash", nullable = false, length = 64, updatable = false)
    private String tokenHash;

    @Column(name = "created_at", nullable = false, updatable = false, columnDefinition = "timestamp with time zone")
    private Instant createdAt;

    @Column(name = "consumed_at", columnDefinition = "timestamp with time zone")
    private Instant consumedAt;

    public UserRefreshToken(UserSession session, String tokenHash, Instant createdAt) {
        this.session = session;
        this.tokenHash = tokenHash;
        this.createdAt = createdAt;
    }
}
