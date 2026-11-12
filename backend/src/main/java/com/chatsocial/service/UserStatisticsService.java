package com.chatsocial.service;

import com.chatsocial.dto.user.UserStatisticsResponse;
import com.chatsocial.enums.UserStatus;
import com.chatsocial.exception.ApiException;
import com.chatsocial.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class UserStatisticsService {
    private final UserRepository users;
    private final PostRepository posts;
    private final FriendshipRepository friendships;
    private final PostLikeRepository likes;
    private final CommentRepository comments;

    @Transactional(readOnly = true)
    public UserStatisticsResponse get(String userId) {
        users.findById(userId).filter(user -> user.getStatus() == UserStatus.ACTIVE)
                .orElseThrow(() -> new ApiException(HttpStatus.FORBIDDEN, "Tài khoản không còn hoạt động"));
        return new UserStatisticsResponse(
                posts.countByUserId(userId),
                friendships.countActiveFriends(userId, UserStatus.ACTIVE),
                likes.countReceivedLikes(userId),
                comments.countReceivedComments(userId, UserStatus.ACTIVE));
    }
}
