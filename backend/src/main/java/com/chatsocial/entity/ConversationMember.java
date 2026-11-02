package com.chatsocial.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.Instant;
import org.hibernate.annotations.CreationTimestamp;

@Entity
@Table(
        name = "conversation_members",
        schema = "chatsocial",
        indexes = {
                @Index(name = "idx_conversation_members_user", columnList = "user_id")
        },
        check = {
                @CheckConstraint(name = "ck_conversation_members_left", constraint = "left_at IS NULL OR left_at >= joined_at")
        }
)
@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PUBLIC)
public class ConversationMember {

    @EmbeddedId
    @Setter(AccessLevel.NONE)
    private ConversationMemberId id = new ConversationMemberId();

    @MapsId("conversationId")
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "conversation_id", referencedColumnName = "conversation_id", nullable = false, updatable = false)
    private Conversation conversation;

    @MapsId("userId")
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", referencedColumnName = "user_id", nullable = false, updatable = false)
    private User user;

    @CreationTimestamp
    @Column(name = "joined_at", nullable = false, updatable = false,
            columnDefinition = "timestamp with time zone")
    @Setter(AccessLevel.NONE)
    private Instant joinedAt;

    
    @Column(name = "left_at",
            columnDefinition = "timestamp with time zone")
    private Instant leftAt;

    public ConversationMember(Conversation conversation, User user) {
        if (conversation == null || user == null
                || conversation.getId() == null || user.getId() == null) {
            throw new IllegalArgumentException("Conversation and user must already be saved");
        }
        this.conversation = conversation;
        this.user = user;
        this.id = new ConversationMemberId(conversation.getId(), user.getId());
    }
}
