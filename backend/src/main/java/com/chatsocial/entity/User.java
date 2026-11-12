package com.chatsocial.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.Instant;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;
import java.util.Locale;
import com.chatsocial.enums.UserStatus;

@Entity
@Table(
        name = "users",
        schema = "chatsocial",
        uniqueConstraints = {
                @UniqueConstraint(name = "uq_users_email", columnNames = {"email"}),
                @UniqueConstraint(name = "uq_users_username", columnNames = {"username"})
        },
        check = {
                @CheckConstraint(name = "ck_users_email_not_blank", constraint = "length(trim(email)) > 0"),
                @CheckConstraint(name = "ck_users_username_not_blank", constraint = "length(trim(username)) > 0")
        }
)
@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PUBLIC)
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "user_id")
    @Setter(AccessLevel.NONE)
    private String id;

    @Column(name = "email", nullable = false, length = 255)
    private String email;

    @Column(name = "password_hash", nullable = false, length = 255)
    private String passwordHash;

    @Column(name = "username", nullable = false, length = 50)
    private String username;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private UserStatus status = UserStatus.ACTIVE;

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

    @Column(
            name = "last_seen_at",
            columnDefinition = "timestamp with time zone"
    )
    private Instant lastSeenAt;

    @PrePersist
    @PreUpdate
    private void normalizeIdentity() {
        if (email != null) {
            email = email.trim().toLowerCase(Locale.ROOT);
        }
        if (username != null) {
            username = username.trim().toLowerCase(Locale.ROOT);
        }
        if (status == null) {
            status = UserStatus.ACTIVE;
        }
    }

    public User(String id, String email, String passwordHash, String username, UserStatus status, Instant createdAt, Instant updatedAt) {
        this(id, email, passwordHash, username, status, createdAt, updatedAt, null);
    }
}
