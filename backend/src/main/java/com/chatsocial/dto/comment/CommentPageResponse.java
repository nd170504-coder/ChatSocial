package com.chatsocial.dto.comment;

import java.util.List;

public record CommentPageResponse(
        List<CommentResponse> items,
        int page,
        int size,
        boolean hasNext
) {}
