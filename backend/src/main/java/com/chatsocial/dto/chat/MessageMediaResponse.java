package com.chatsocial.dto.chat;

import com.chatsocial.enums.MediaType;

public record MessageMediaResponse(
        String id,
        String url,
        MediaType mediaType
) {}
