package com.chatsocial.Controller;

import com.chatsocial.dto.ApiResponse;
import com.chatsocial.dto.friend.FriendPageResponse;
import com.chatsocial.service.FriendService;
import com.chatsocial.utils.SecurityUtils;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping(
        value = "/api/friends",
        produces = MediaType.APPLICATION_JSON_VALUE
)
@Tag(name = "Friends", description = "Danh sách bạn bè")
public class FriendController {

    private final FriendService friendService;

    @GetMapping
    @Operation(summary = "Lấy danh sách bạn bè của tôi")
    public ResponseEntity<ApiResponse<FriendPageResponse>> getFriends(
            @RequestParam(name = "page", defaultValue = "0") int page,
            @RequestParam(name = "size", defaultValue = "20") int size
    ) {
        String currentUserId = SecurityUtils.getCurrentUserId();
        FriendPageResponse response = friendService.getFriends(currentUserId, page, size);

        return ResponseEntity.ok(ApiResponse.success("Friends retrieved successfully", response));
    }

}
