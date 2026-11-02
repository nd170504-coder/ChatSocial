package com.chatsocial.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDate;
import com.chatsocial.enums.Gender;

@Entity
@Table(
        name = "user_profile",
        schema = "chatsocial",
        uniqueConstraints = {
                @UniqueConstraint(name = "uq_user_profile_user", columnNames = {"user_id"})
        },
        check = {
                @CheckConstraint(name = "ck_user_profile_full_name", constraint = "length(trim(full_name)) > 0")
        }
)
@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PUBLIC)
public class UserProfile {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "profile_id")
    @Setter(AccessLevel.NONE)
    private String id;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", referencedColumnName = "user_id", nullable = false, updatable = false)
    private User user;

    @Column(name = "full_name", nullable = false, length = 100)
    private String fullName;

    @Column(name = "avatar_url", columnDefinition = "text")
    private String avatarUrl;

    @Column(name = "cover_url", columnDefinition = "text")
    private String coverUrl;

    @Column(name = "bio", columnDefinition = "text")
    private String bio;

    @Column(name = "date_of_birth")
    private LocalDate dateOfBirth;

    @Enumerated(EnumType.STRING)
    @Column(name = "gender", length = 20)
    private Gender gender;
}
