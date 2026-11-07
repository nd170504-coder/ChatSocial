package com.chatsocial.dto.post;

public record PostLikeResponse(
        String postId,
        long likeCount,
        boolean likedByMe
) {}
