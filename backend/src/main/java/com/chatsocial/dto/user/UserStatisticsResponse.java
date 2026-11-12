package com.chatsocial.dto.user;

public record UserStatisticsResponse(long postCount, long friendCount, long receivedLikeCount,
                                     long receivedCommentCount) {
}
