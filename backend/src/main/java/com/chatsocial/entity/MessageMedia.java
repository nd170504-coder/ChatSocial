package com.chatsocial.entity;

import jakarta.persistence.*;
import lombok.*;
import com.chatsocial.enums.MediaType;

@Entity
@Table(
        name = "message_media",
        schema = "chatsocial",
        indexes = {
                @Index(name = "idx_message_media_message", columnList = "message_id")
        },
        check = {
                @CheckConstraint(name = "ck_message_media_url", constraint = "length(trim(media_url)) > 0")
        }
)
@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PUBLIC)
public class MessageMedia {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "media_id")
    @Setter(AccessLevel.NONE)
    private String id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "message_id", referencedColumnName = "message_id", nullable = false, updatable = false)
    private Message message;

    @Column(name = "media_url", nullable = false, columnDefinition = "text")
    private String mediaUrl;

    @Enumerated(EnumType.STRING)
    @Column(name = "media_type", nullable = false, length = 10)
    private MediaType mediaType;

    public MessageMedia(Message message, String storagePath) {
        this.message = message;
        this.mediaUrl = storagePath;
        this.mediaType = MediaType.IMAGE;
    }
}
