package com.chatsocial.dto.block;

import java.util.List;
public record BlockPageResponse(List<BlockedUserResponse> items, int page, int size, boolean hasNext) {}
