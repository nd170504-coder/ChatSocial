package com.chatsocial.dto.profile;

import com.chatsocial.entity.UserProfile;
import com.chatsocial.enums.Gender;
import lombok.Getter;
import lombok.AllArgsConstructor;
import java.time.LocalDate;

@Getter
@AllArgsConstructor
public class ProfileResponse {
    private String userId;
    private String fullName;
    private String avatarUrl;
    private String coverUrl;
    private String bio;
    private LocalDate dateOfBirth;
    private Gender gender;

    public static ProfileResponse from(UserProfile profile) {
        return new ProfileResponse(profile.getUser().getId(), profile.getFullName(), profile.getAvatarUrl(),
                profile.getCoverUrl(), profile.getBio(), profile.getDateOfBirth(), profile.getGender());
    }
}
