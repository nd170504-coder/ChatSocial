package com.chatsocial.entity;

import jakarta.persistence.*;
import lombok.*;
import com.chatsocial.enums.MediaType;

@Entity
@Table(
        name = "post_media",
        schema = "chatsocial",
        uniqueConstraints = {
                @UniqueConstraint(name = "uq_post_media_order", columnNames = {"post_id", "display_order"})
        },
        check = {
                @CheckConstraint(name = "ck_post_media_content", constraint = "display_order >= 0 AND length(trim(media_url)) > 0")
        }
)
@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PUBLIC)
public class PostMedia {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "media_id")
    @Setter(AccessLevel.NONE)
    private String id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "post_id", referencedColumnName = "post_id", nullable = false, updatable = false)
    private Post post;

    @Column(name = "media_url", nullable = false, columnDefinition = "text")
    private String mediaUrl;

    @Enumerated(EnumType.STRING)
    @Column(name = "media_type", nullable = false, length = 10)
    private MediaType mediaType;

    @Column(name = "display_order", nullable = false)
    private Integer displayOrder = 0;
}
