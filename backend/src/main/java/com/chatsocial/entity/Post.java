package com.chatsocial.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.Instant;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;
import com.chatsocial.enums.PostPrivacy;

@Entity
@Table(
        name = "posts",
        schema = "chatsocial",
        indexes = {
                @Index(name = "idx_posts_user_created", columnList = "user_id, created_at DESC, post_id DESC")
        },
        check = {
                @CheckConstraint(name = "ck_posts_content", constraint = "content IS NULL OR length(trim(content)) > 0")
        }
)
@Getter
@Setter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PUBLIC)
public class Post {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "post_id")
    @Setter(AccessLevel.NONE)
    private String id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", referencedColumnName = "user_id", nullable = false, updatable = false)
    private User user;

    @Column(name = "content", columnDefinition = "text")
    private String content;

    @Enumerated(EnumType.STRING)
    @Column(name = "privacy", nullable = false, length = 20)
    private PostPrivacy privacy = PostPrivacy.FRIENDS;

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
}
