package com.chatsocial.service;

import com.chatsocial.dto.friend.FriendCountResponse;
import com.chatsocial.dto.friend.FriendPageResponse;
import com.chatsocial.dto.friend.FriendResponse;
import com.chatsocial.entity.User;
import com.chatsocial.entity.UserProfile;
import com.chatsocial.enums.UserStatus;
import com.chatsocial.exception.ApiException;
import com.chatsocial.repository.FriendshipRepository;
import com.chatsocial.repository.UserProfileRepository;
import com.chatsocial.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Service
@RequiredArgsConstructor
public class FriendService {

    private final FriendshipRepository friendshipRepository;
    private final UserProfileRepository profileRepository;
    private final UserRepository userRepository;
    private final BlockPolicy blockPolicy;

    @Transactional(readOnly = true)
    public FriendPageResponse getFriends(String currentUserId, int page, int size) {
        if (page < 0 || size < 1 || size > 50) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "page phải từ 0 trở lên và size phải từ 1 đến 50");
        }

        var pageable = PageRequest.of(page, size, Sort.by("username", "id"));

        var friends = friendshipRepository.findFriends(currentUserId, UserStatus.ACTIVE, pageable);

        List<String> userIds = friends.getContent()
                .stream()
                .map(User::getId)
                .toList();

        Map<String, UserProfile> profiles = userIds.isEmpty()
                ? Map.of()
                : profileRepository.findByUserIdIn(userIds)
                .stream()
                .collect(Collectors.toMap(
                        profile -> profile.getUser().getId(),
                        profile -> profile
                ));

        List<FriendResponse> items = friends.getContent()
                .stream()
                .map(user -> {
                    UserProfile profile = profiles.get(user.getId());

                    return new FriendResponse(user.getId(), user.getUsername(),
                            profile != null
                                    ? profile.getFullName()
                                    : user.getUsername(),
                            profile != null
                                    ? profile.getAvatarUrl()
                                    : null
                    );
                }).toList();

        return new FriendPageResponse(items, friends.getNumber(), friends.getSize(), friends.hasNext());
    }

    @Transactional(readOnly = true)
    public FriendCountResponse countFriends(String viewerId, String userId) {
        blockPolicy.requireVisible(viewerId, userId);
        userRepository.findById(userId)
                .filter(user -> user.getStatus() == UserStatus.ACTIVE)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "User not found"));

        long count = friendshipRepository.countActiveFriends(userId, UserStatus.ACTIVE);
        return new FriendCountResponse(userId, count);
    }

    @Transactional
    public void unfriend(String currentUserId, String otherUserId) {
        if (currentUserId.equals(otherUserId)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Không thể hủy kết bạn với chính mình");
        }

        var userIds = Stream.of(currentUserId, otherUserId)
                .sorted()
                .toList();

        for (String userId : userIds) {
            User user = userRepository.findForUpdateById(userId)
                    .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Không tìm thấy người dùng"));

            if (userId.equals(currentUserId) && user.getStatus() != UserStatus.ACTIVE) {
                throw new ApiException(HttpStatus.UNAUTHORIZED, "Tài khoản không còn hoạt động");
            }
        }

        int deleted = friendshipRepository.deleteFriendship(currentUserId, otherUserId);

        if (deleted == 0) {
            throw new ApiException(HttpStatus.NOT_FOUND, "Hai người hiện không phải bạn bè");
        }
    }

}
