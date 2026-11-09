package com.chatsocial.dto.chat;

import java.util.List;

public record ConversationPageResponse(
        List<ConversationResponse> items,
        int page,
        int size,
        boolean hasNext
) {}
