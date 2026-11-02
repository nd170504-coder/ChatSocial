package com.chatsocial.entity;

import com.chatsocial.enums.NotificationType;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.Check;
import org.hibernate.annotations.CreationTimestamp;

import java.time.Instant;

@Entity
@Table(
        name = "notifications",
        schema = "chatsocial",
        indexes = {
                @Index(
                        name = "idx_notifications_recipient_created",
                        columnList = "recipient_id, created_at DESC, notification_id DESC"
                ),
                @Index(
                        name = "idx_notifications_actor",
                        columnList = "actor_id"
                ),
                @Index(
                        name = "idx_notifications_post",
                        columnList = "post_id"
                )
        }
)
@Check(
        constraints = """
                recipient_id <> actor_id
                AND (
                    (
                        type IN ('POST_LIKED', 'POST_COMMENTED')
                        AND post_id IS NOT NULL
                    )
                    OR
                    (
                        type IN ('FRIEND_REQUEST', 'FRIEND_ACCEPTED')
                        AND post_id IS NULL
                    )
                )
                """
)
@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PUBLIC)
public class Notification {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "notification_id")
    @Setter(AccessLevel.NONE)
    private String id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(
            name = "recipient_id",
            referencedColumnName = "user_id",
            nullable = false,
            updatable = false
    )
    private User recipient;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(
            name = "actor_id",
            referencedColumnName = "user_id",
            nullable = false,
            updatable = false
    )
    private User actor;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(
            name = "post_id",
            referencedColumnName = "post_id",
            updatable = false
    )
    private Post post;

    @Enumerated(EnumType.STRING)
    @Column(
            name = "type",
            nullable = false,
            updatable = false,
            length = 30
    )
    private NotificationType type;

    @CreationTimestamp
    @Column(
            name = "created_at",
            nullable = false,
            updatable = false,
            columnDefinition = "timestamp with time zone"
    )
    @Setter(AccessLevel.NONE)
    private Instant createdAt;

    @Column(
            name = "read_at",
            columnDefinition = "timestamp with time zone"
    )
    private Instant readAt;
}
