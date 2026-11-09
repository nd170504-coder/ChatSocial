package com.chatsocial.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.Instant;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

@Entity
@Table(
        name = "conversations",
        schema = "chatsocial"
)
@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Conversation {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "conversation_id")
    @Setter(AccessLevel.NONE)
    private String id;

    @Column(
            name = "direct_pair_key",
            unique = true,
            length = 73,
            updatable = false
    )
    private String directPairKey;

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

    public Conversation(String directPairKey) {
        this.directPairKey = directPairKey;
    }

    public void touch() {
        this.updatedAt = Instant.now();
    }
}
