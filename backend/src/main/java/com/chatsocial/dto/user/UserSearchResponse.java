package com.chatsocial.dto.user;

import java.util.List;

public record UserSearchResponse(List<UserSearchItem> items, int page, int size, boolean hasNext) {}
