package com.chatsocial.dto.chat;

import java.util.List;

public record MessagePageResponse(
        List<MessageResponse> items,
        boolean hasNext,
        String nextBefore
) {}
