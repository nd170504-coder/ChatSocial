package com.chatsocial.dto.profile;

import com.chatsocial.enums.Gender;
import java.time.LocalDate;

public record UserProfileResponse(String userId, String username, String fullName, String avatarUrl,
                                  String coverUrl, String bio, ProfileRelationship relationship, String friendRequestId,
                                  LocalDate dateOfBirth, Gender gender) {}
