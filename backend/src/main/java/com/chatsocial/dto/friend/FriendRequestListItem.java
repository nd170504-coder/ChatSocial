package com.chatsocial.dto.friend;

import com.chatsocial.dto.user.UserSearchItem;
import com.chatsocial.enums.FriendshipStatus;
import java.time.Instant;

public record FriendRequestListItem(String id, UserSearchItem user, FriendshipStatus status, Instant sentAt) {}
