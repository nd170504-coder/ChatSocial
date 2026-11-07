package com.chatsocial.dto.comment;

import java.time.Instant;

public record CommentResponse(
        String id,
        String postId,
        String userId,
        String fullName,
        String avatarUrl,
        String content,
        Instant createdAt
) {}
