package com.chatsocial.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.Instant;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;
import com.chatsocial.enums.FriendshipStatus;

@Entity
@Table(
        name = "friend_requests",
        schema = "chatsocial",
        uniqueConstraints = {
                @UniqueConstraint(name = "uq_friend_requests_pair", columnNames = {"pair_key"})
        },
        indexes = {
                @Index(name = "idx_friend_requests_incoming", columnList = "addressee_id, status"),
                @Index(name = "idx_friend_requests_outgoing", columnList = "requester_id, status")
        },
        check = {
                @CheckConstraint(name = "ck_friend_requests_different_users", constraint = "requester_id <> addressee_id")
        }
)
@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PUBLIC)
public class FriendRequest {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "request_id")
    @Setter(AccessLevel.NONE)
    private String id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "requester_id", referencedColumnName = "user_id", nullable = false)
    private User requester;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "addressee_id", referencedColumnName = "user_id", nullable = false)
    private User addressee;

    @Column(name = "pair_key", nullable = false, length = 73)
    @Setter(AccessLevel.NONE)
    private String pairKey;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private FriendshipStatus status = FriendshipStatus.PENDING;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false,
            columnDefinition = "timestamp with time zone")
    @Setter(AccessLevel.NONE)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false,
            columnDefinition = "timestamp with time zone")
    @Setter(AccessLevel.NONE)
    private Instant updatedAt;

    public FriendRequest(User requester, User addressee) {
        this.requester = requester;
        this.addressee = addressee;
        this.status = FriendshipStatus.PENDING;
    }

    @PrePersist
    @PreUpdate
    private void normalizePair() {
        if (requester == null || addressee == null
                || requester.getId() == null || addressee.getId() == null) {
            throw new IllegalStateException("Both users must already be saved");
        }
        String first = requester.getId();
        String second = addressee.getId();
        int order = first.compareTo(second);
        if (order == 0) {
            throw new IllegalStateException("Cannot send a request to yourself");
        }
        pairKey = order < 0 ? first + ":" + second : second + ":" + first;
        if (status == null) {
            status = FriendshipStatus.PENDING;
        }
    }
}
