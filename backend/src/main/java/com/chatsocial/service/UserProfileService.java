package com.chatsocial.service;

import com.chatsocial.dto.profile.ProfileRelationship;
import com.chatsocial.dto.profile.UserProfileResponse;
import com.chatsocial.entity.UserProfile;
import com.chatsocial.enums.UserStatus;
import com.chatsocial.enums.FriendshipStatus;
import com.chatsocial.repository.FriendRequestRepository;
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
    private final FriendRequestRepository requests;

    @Transactional(readOnly = true)
    public UserProfileResponse get(String viewerId, String userId) {
        var user = users.findById(userId).filter(value -> value.getStatus() == UserStatus.ACTIVE)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "User profile not found"));
        UserProfile profile = profiles.findByUserId(userId).orElse(null);
        RelationshipInfo relationship = relationship(viewerId, userId);
        return new UserProfileResponse(user.getId(), user.getUsername(),
                profile == null ? user.getUsername() : profile.getFullName(),
                profile == null ? null : profile.getAvatarUrl(),
                profile == null ? null : profile.getCoverUrl(),
                profile == null ? null : profile.getBio(), relationship.status(), relationship.requestId());
    }

    private RelationshipInfo relationship(String viewerId, String userId) {
        if (viewerId.equals(userId)) return new RelationshipInfo(ProfileRelationship.SELF, null);
        if (friendships.areFriends(viewerId, userId)) return new RelationshipInfo(ProfileRelationship.FRIENDS, null);
        String pairKey = viewerId.compareTo(userId) < 0 ? viewerId + ":" + userId : userId + ":" + viewerId;
        return requests.findByPairKey(pairKey)
                .filter(request -> request.getStatus() == FriendshipStatus.PENDING)
                .map(request -> new RelationshipInfo(request.getRequester().getId().equals(viewerId)
                        ? ProfileRelationship.OUTGOING_PENDING : ProfileRelationship.INCOMING_PENDING, request.getId()))
                .orElse(new RelationshipInfo(ProfileRelationship.NONE, null));
    }

    private record RelationshipInfo(ProfileRelationship status, String requestId) {}
}
