package com.chatsocial.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import java.time.Instant;

@Entity
@Table(name = "user_blocks", schema = "chatsocial",
        uniqueConstraints = @UniqueConstraint(name = "uq_user_blocks_pair", columnNames = {"blocker_id", "blocked_id"}),
        indexes = @Index(name = "idx_user_blocks_blocked", columnList = "blocked_id"),
        check = @CheckConstraint(name = "ck_user_blocks_different", constraint = "blocker_id <> blocked_id"))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class UserBlock {
    @Id @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "block_id")
    private String id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "blocker_id", nullable = false, updatable = false)
    private User blocker;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "blocked_id", nullable = false, updatable = false)
    private User blocked;
    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false, columnDefinition = "timestamp with time zone")
    private Instant createdAt;

    public UserBlock(User blocker, User blocked) { this.blocker = blocker; this.blocked = blocked; }
}
