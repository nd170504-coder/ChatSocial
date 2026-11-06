package com.chatsocial.service;

import com.chatsocial.dto.profile.ProfileRelationship;
import com.chatsocial.dto.profile.UserProfileResponse;
import com.chatsocial.entity.UserProfile;
import com.chatsocial.enums.UserStatus;
import com.chatsocial.exception.ApiException;
import com.chatsocial.repository.FriendshipRepository;
import com.chatsocial.repository.UserProfileRepository;
import com.chatsocial.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class UserProfileService {
    private final UserRepository users;
    private final UserProfileRepository profiles;
    private final FriendshipRepository friendships;

    @Transactional(readOnly = true)
    public UserProfileResponse get(String viewerId, String userId) {
        var user = users.findById(userId).filter(value -> value.getStatus() == UserStatus.ACTIVE)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "User profile not found"));
        UserProfile profile = profiles.findByUserId(userId).orElse(null);
        ProfileRelationship relationship = viewerId.equals(userId) ? ProfileRelationship.SELF
                : friendships.areFriends(viewerId, userId) ? ProfileRelationship.FRIENDS : ProfileRelationship.NONE;
        return new UserProfileResponse(user.getId(), user.getUsername(),
                profile == null ? user.getUsername() : profile.getFullName(),
                profile == null ? null : profile.getAvatarUrl(),
                profile == null ? null : profile.getCoverUrl(),
                profile == null ? null : profile.getBio(), relationship);
    }
}
