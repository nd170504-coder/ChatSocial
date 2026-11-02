package com.chatsocial.dto.profile;

import com.chatsocial.entity.UserProfile;
import com.chatsocial.enums.Gender;
import java.time.LocalDate;

public record ProfileResponse(String userId, String fullName, String avatarUrl, String coverUrl,
                              String bio, LocalDate dateOfBirth, Gender gender) {
    public static ProfileResponse from(UserProfile profile) {
        return new ProfileResponse(profile.getUser().getId(), profile.getFullName(), profile.getAvatarUrl(),
                profile.getCoverUrl(), profile.getBio(), profile.getDateOfBirth(), profile.getGender());
    }
}
