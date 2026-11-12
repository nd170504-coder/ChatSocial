package com.chatsocial.dto.block;

public record BlockStatusResponse(String userId, boolean blockedByMe, boolean interactionBlocked) {}
