package com.chatsocial.dto.friend;

import java.util.List;

public record FriendRequestPageResponse(List<FriendRequestListItem> items, int page, int size, boolean hasNext) {}
