package com.chatsocial.service;

import com.chatsocial.dto.user.UserSearchItem;
import com.chatsocial.dto.user.UserSearchResponse;
import com.chatsocial.entity.UserProfile;
import com.chatsocial.enums.UserStatus;
import com.chatsocial.exception.ApiException;
import com.chatsocial.repository.UserProfileRepository;
import com.chatsocial.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class UserSearchService {
    private final UserRepository users;
    private final UserProfileRepository profiles;

    @Transactional(readOnly = true)
    public UserSearchResponse search(String currentUserId, String query, int page, int size) {
        String normalized = query == null ? "" : query.strip().toLowerCase(Locale.ROOT);
        if (normalized.startsWith("@")) normalized = normalized.substring(1);
        if (normalized.length() < 2 || normalized.length() > 255) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Từ khóa tìm kiếm phải có từ 2 đến 255 ký tự");
        }
        if (page < 0 || size < 1 || size > 50) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "page phải từ 0 trở lên và size phải từ 1 đến 50");
        }
        // Escape LIKE metacharacters: underscores in usernames must match literally.
        String pattern = "%" + normalized.replace("!", "!!").replace("%", "!%")
                .replace("_", "!_") + "%";
        var result = users.searchActiveUsers(currentUserId, UserStatus.ACTIVE, normalized.contains("@"),
                normalized, pattern, PageRequest.of(page, size, Sort.by("username", "id")));
        var ids = result.getContent().stream().map(user -> user.getId()).toList();
        Map<String, UserProfile> profileByUser = ids.isEmpty() ? Map.of()
                : profiles.findByUserIdIn(ids).stream().collect(Collectors.toMap(
                        profile -> profile.getUser().getId(), profile -> profile));
        var items = result.getContent().stream().map(user -> {
            UserProfile profile = profileByUser.get(user.getId());
            return new UserSearchItem(user.getId(), user.getUsername(),
                    profile == null ? user.getUsername() : profile.getFullName(),
                    profile == null ? null : profile.getAvatarUrl());
        }).toList();
        return new UserSearchResponse(items, result.getNumber(), result.getSize(), result.hasNext());
    }
}
