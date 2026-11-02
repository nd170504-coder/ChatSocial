package com.chatsocial.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.Instant;
import org.hibernate.annotations.CreationTimestamp;

@Entity
@Table(
        name = "message_reads",
        schema = "chatsocial",
        indexes = {
                @Index(name = "idx_message_reads_user", columnList = "user_id")
        }
)
@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PUBLIC)
public class MessageRead {

    @EmbeddedId
    @Setter(AccessLevel.NONE)
    private MessageReadId id = new MessageReadId();

    @MapsId("messageId")
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "message_id", referencedColumnName = "message_id", nullable = false, updatable = false)
    private Message message;

    @MapsId("userId")
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", referencedColumnName = "user_id", nullable = false, updatable = false)
    private User user;

    @CreationTimestamp
    @Column(name = "read_at", nullable = false, updatable = false,
            columnDefinition = "timestamp with time zone")
    @Setter(AccessLevel.NONE)
    private Instant readAt;

    public MessageRead(Message message, User user) {
        if (message == null || user == null
                || message.getId() == null || user.getId() == null) {
            throw new IllegalArgumentException("Message and user must already be saved");
        }
        this.message = message;
        this.user = user;
        this.id = new MessageReadId(message.getId(), user.getId());
    }
}
