package com.chatsocial.dto.friend;

import com.chatsocial.entity.FriendRequest;
import com.chatsocial.enums.FriendshipStatus;
import java.time.Instant;

public record FriendRequestResponse(String id, String requesterId, String addresseeId,
                                     FriendshipStatus status, Instant createdAt, Instant updatedAt) {
    public static FriendRequestResponse from(FriendRequest request) {
        return new FriendRequestResponse(request.getId(), request.getRequester().getId(),
                request.getAddressee().getId(), request.getStatus(), request.getCreatedAt(), request.getUpdatedAt());
    }
}
