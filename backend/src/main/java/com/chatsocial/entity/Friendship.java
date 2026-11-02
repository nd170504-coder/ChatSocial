package com.chatsocial.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.Instant;
import org.hibernate.annotations.CreationTimestamp;

@Entity
@Table(
        name = "friendships",
        schema = "chatsocial",
        uniqueConstraints = {
                @UniqueConstraint(name = "uq_friendships_users", columnNames = {"user_id", "friend_id"})
        },
        indexes = {
                @Index(name = "idx_friendships_friend", columnList = "friend_id")
        },
        check = {
                @CheckConstraint(name = "ck_friendships_ordered_pair", constraint = "user_id < friend_id")
        }
)
@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PUBLIC)
public class Friendship {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "friendship_id")
    @Setter(AccessLevel.NONE)
    private String id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", referencedColumnName = "user_id", nullable = false, updatable = false)
    private User user;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "friend_id", referencedColumnName = "user_id", nullable = false, updatable = false)
    private User friend;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false,
            columnDefinition = "timestamp with time zone")
    @Setter(AccessLevel.NONE)
    private Instant createdAt;

    public Friendship(User user, User friend) {
        this.user = user;
        this.friend = friend;
    }

    @PrePersist
    private void normalizePair() {
        if (user == null || friend == null
                || user.getId() == null || friend.getId() == null) {
            throw new IllegalStateException("Both users must already be saved");
        }
        int order = user.getId().compareTo(friend.getId());
        if (order == 0) {
            throw new IllegalStateException("Cannot be friends with yourself");
        }
        if (order > 0) {
            User originalUser = user;
            user = friend;
            friend = originalUser;
        }
    }
}
