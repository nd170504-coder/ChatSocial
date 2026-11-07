package com.chatsocial.dto.comment;

public record CommentCountResponse(
        String postId,
        long commentCount
) {}
