package com.chatsocial.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.Instant;
import org.hibernate.annotations.CreationTimestamp;

@Entity
@Table(
        name = "messages",
        schema = "chatsocial",
        indexes = {
                @Index(name = "idx_messages_conversation_created", columnList = "conversation_id, created_at DESC, message_id DESC"),
                @Index(name = "idx_messages_sender", columnList = "sender_id")
        },
        check = {
                @CheckConstraint(name = "ck_messages_content", constraint = "content IS NULL OR length(trim(content)) > 0")
        }
)
@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PUBLIC)
public class Message {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "message_id")
    @Setter(AccessLevel.NONE)
    private String id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "conversation_id", referencedColumnName = "conversation_id", nullable = false, updatable = false)
    private Conversation conversation;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "sender_id", referencedColumnName = "user_id", nullable = false, updatable = false)
    private User sender;

    // Read-only relation adds a DB foreign key enforcing sender membership.
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumns(
            value = {
                    @JoinColumn(
                            name = "conversation_id",
                            referencedColumnName = "conversation_id",
                            insertable = false,
                            updatable = false
                    ),
                    @JoinColumn(
                            name = "sender_id",
                            referencedColumnName = "user_id",
                            insertable = false,
                            updatable = false
                    )
            },
            foreignKey = @ForeignKey(name = "fk_messages_sender_member")
    )
    @Setter(AccessLevel.NONE)
    private ConversationMember senderMembership;

    @Column(name = "content", columnDefinition = "text")
    private String content;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false,
            columnDefinition = "timestamp with time zone")
    @Setter(AccessLevel.NONE)
    private Instant createdAt;

    
    @Column(name = "deleted_at",
            columnDefinition = "timestamp with time zone")
    private Instant deletedAt;
}
