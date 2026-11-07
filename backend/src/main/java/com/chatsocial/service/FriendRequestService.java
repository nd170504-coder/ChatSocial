package com.chatsocial.service;

import com.chatsocial.dto.friend.FriendRequestResponse;
import com.chatsocial.dto.friend.SendFriendRequest;
import com.chatsocial.dto.friend.FriendRequestListItem;
import com.chatsocial.dto.friend.FriendRequestPageResponse;
import com.chatsocial.dto.user.UserSearchItem;
import com.chatsocial.entity.UserProfile;
import com.chatsocial.entity.FriendRequest;
import com.chatsocial.entity.User;
import com.chatsocial.entity.Friendship;
import com.chatsocial.enums.FriendshipStatus;
import com.chatsocial.enums.NotificationType;
import com.chatsocial.enums.UserStatus;
import com.chatsocial.exception.ApiException;
import com.chatsocial.repository.FriendRequestRepository;
import com.chatsocial.repository.FriendshipRepository;
import com.chatsocial.repository.UserRepository;
import com.chatsocial.repository.UserProfileRepository;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import java.util.Map;
import java.util.stream.Collectors;
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
    private final UserProfileRepository profiles;
    private final NotificationService notificationService;

    @Transactional(readOnly = true)
    public FriendRequestPageResponse list(String userId, boolean incoming, int page, int size) {
        if (page < 0 || size < 1 || size > 50) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "page phải từ 0 trở lên và size phải từ 1 đến 50");
        }
        // updatedAt is the most recent send time when a concluded pair's invitation is reopened.
        var pageable = PageRequest.of(page, size, Sort.by(Sort.Order.desc("updatedAt"), Sort.Order.desc("id")));
        var result = incoming ? requests.findIncoming(userId, FriendshipStatus.PENDING, UserStatus.ACTIVE, pageable)
                : requests.findOutgoing(userId, FriendshipStatus.PENDING, UserStatus.ACTIVE, pageable);
        var ids = result.getContent().stream()
                .map(request -> incoming ? request.getRequester().getId() : request.getAddressee().getId()).toList();
        Map<String, UserProfile> byUser = ids.isEmpty() ? Map.of() : profiles.findByUserIdIn(ids).stream()
                .collect(Collectors.toMap(profile -> profile.getUser().getId(), profile -> profile));
        var items = result.getContent().stream().map(request -> {
            User other = incoming ? request.getRequester() : request.getAddressee();
            UserProfile profile = byUser.get(other.getId());
            var summary = new UserSearchItem(other.getId(), other.getUsername(),
                    profile == null ? other.getUsername() : profile.getFullName(),
                    profile == null ? null : profile.getAvatarUrl());
            return new FriendRequestListItem(request.getId(), summary, request.getStatus(), request.getUpdatedAt());
        }).toList();
        return new FriendRequestPageResponse(items, result.getNumber(), result.getSize(), result.hasNext());
    }

    @Transactional
    public FriendRequestResponse send(String requesterId, SendFriendRequest input) {
        String addresseeId = input.userId();
        if (requesterId.equals(addresseeId)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Cannot send a friend request to yourself");
        }

        // Lock both users in a fixed order, even when no request row exists yet.
        // Accept/reject/cancel/unfriend operations must follow the same lock order.
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
        requests.saveAndFlush(request);

        notificationService.create(
                addressee,
                requester,
                null,
                NotificationType.FRIEND_REQUEST
        );

        return FriendRequestResponse.from(request);
    }

    @Transactional
    public void cancel(String requesterId, String requestId) {
        FriendRequest request = lockedRequest(requesterId, requestId, false);
        if (!request.getRequester().getId().equals(requesterId)) throw requestNotFound();
        if (request.getStatus() != FriendshipStatus.PENDING
                || friendships.areFriends(requesterId, request.getAddressee().getId())) {
            throw new ApiException(HttpStatus.CONFLICT, "Only pending friend requests can be cancelled");
        }
        // Cancelling removes only a pending invitation; it never removes a friendship.
        requests.delete(request);
        requests.flush();
    }

    @Transactional
    public FriendRequestResponse respond(String recipientId, String requestId, boolean accept) {
        FriendRequest request = lockedRequest(recipientId, requestId, accept);
        if (!request.getAddressee().getId().equals(recipientId)) throw requestNotFound();
        if (request.getStatus() != FriendshipStatus.PENDING
                || friendships.areFriends(recipientId, request.getRequester().getId())) {
            throw new ApiException(HttpStatus.CONFLICT, "Friend request has already been processed");
        }
        if (accept) {
            friendships.saveAndFlush(new Friendship(request.getRequester(), request.getAddressee()));
        }
        request.setStatus(accept ? FriendshipStatus.ACCEPTED : FriendshipStatus.REJECTED);
        requests.saveAndFlush(request);
        if (accept) {
            notificationService.create(
                    request.getRequester(),
                    request.getAddressee(),
                    null,
                    NotificationType.FRIEND_ACCEPTED
            );
        }

        return FriendRequestResponse.from(request);
    }

    private FriendRequest lockedRequest(String actorId, String requestId, boolean requireActiveOther) {
        // Read only the stable pair key before locking, so a waiting request cannot use stale entity state.
        String pairKey = requests.findPairKeyById(requestId).orElseThrow(this::requestNotFound);
        String[] pair = pairKey.split(":");
        if (!actorId.equals(pair[0]) && !actorId.equals(pair[1])) throw requestNotFound();
        for (String userId : pair) {
            User user = users.findForUpdateById(userId).orElseThrow(this::requestNotFound);
            if (userId.equals(actorId) && user.getStatus() != UserStatus.ACTIVE) {
                throw new ApiException(HttpStatus.UNAUTHORIZED, "User is unavailable");
            }
            if (!userId.equals(actorId) && requireActiveOther && user.getStatus() != UserStatus.ACTIVE) {
                throw new ApiException(HttpStatus.NOT_FOUND, "User is unavailable");
            }
        }
        return requests.findById(requestId).orElseThrow(this::requestNotFound);
    }

    private ApiException requestNotFound() {
        return new ApiException(HttpStatus.NOT_FOUND, "Friend request not found");
    }

    private User activeUserForUpdate(String userId, String requesterId) {
        return users.findForUpdateById(userId).filter(user -> user.getStatus() == UserStatus.ACTIVE)
                .orElseThrow(() -> new ApiException(userId.equals(requesterId)
                        ? HttpStatus.UNAUTHORIZED : HttpStatus.NOT_FOUND,
                        userId.equals(requesterId) ? "User is unavailable" : "Recipient is unavailable"));
    }
}
