package com.chatsocial.dto.friend;

import java.util.List;

public record FriendPageResponse(
        List<FriendResponse> items,
        int page,
        int size,
        boolean hasNext
) {}
