package com.chatsocial.service;

import com.chatsocial.dto.friend.FriendRequestResponse;
import com.chatsocial.dto.friend.SendFriendRequest;
import com.chatsocial.entity.FriendRequest;
import com.chatsocial.entity.User;
import com.chatsocial.enums.FriendshipStatus;
import com.chatsocial.enums.UserStatus;
import com.chatsocial.exception.ApiException;
import com.chatsocial.repository.FriendRequestRepository;
import com.chatsocial.repository.FriendshipRepository;
import com.chatsocial.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class FriendRequestService {
    private final FriendRequestRepository requests;
    private final FriendshipRepository friendships;
    private final UserRepository users;

    @Transactional
    public FriendRequestResponse send(String requesterId, SendFriendRequest input) {
        String addresseeId = input.userId();
        if (requesterId.equals(addresseeId)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Cannot send a friend request to yourself");
        }

        // Lock both users in a fixed order, even when no request row exists yet.
        // Future accept/reject/cancel/unfriend operations must follow the same lock order.
        boolean senderFirst = requesterId.compareTo(addresseeId) < 0;
        User first = activeUserForUpdate(senderFirst ? requesterId : addresseeId, requesterId);
        User second = activeUserForUpdate(senderFirst ? addresseeId : requesterId, requesterId);
        User requester = senderFirst ? first : second;
        User addressee = senderFirst ? second : first;

        if (friendships.areFriends(requesterId, addresseeId)) {
            throw new ApiException(HttpStatus.CONFLICT, "Users are already friends");
        }
        String pairKey = first.getId() + ":" + second.getId();
        FriendRequest request = requests.findByPairKey(pairKey).orElse(null);
        if (request != null && request.getStatus() == FriendshipStatus.PENDING) {
            throw new ApiException(HttpStatus.CONFLICT, request.getRequester().getId().equals(requesterId)
                    ? "Friend request already sent"
                    : "You have a pending friend request from this user");
        }

        if (request == null) {
            request = new FriendRequest(requester, addressee);
        } else {
            // One row per unordered pair: reopen a concluded invitation, preserving its identity.
            request.setRequester(requester);
            request.setAddressee(addressee);
            request.setStatus(FriendshipStatus.PENDING);
        }
        return FriendRequestResponse.from(requests.saveAndFlush(request));
    }

    private User activeUserForUpdate(String userId, String requesterId) {
        return users.findForUpdateById(userId).filter(user -> user.getStatus() == UserStatus.ACTIVE)
                .orElseThrow(() -> new ApiException(userId.equals(requesterId)
                        ? HttpStatus.UNAUTHORIZED : HttpStatus.NOT_FOUND,
                        userId.equals(requesterId) ? "User is unavailable" : "Recipient is unavailable"));
    }
}
