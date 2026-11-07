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
import org.springframework.web.bind.annotation.*;

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

    @DeleteMapping("/{userId}")
    @Operation(summary = "Hủy kết bạn")
    public ResponseEntity<ApiResponse<Void>> unfriend(@PathVariable("userId") String userId) {
        friendService.unfriend(SecurityUtils.getCurrentUserId(), userId);

        return ResponseEntity.ok(ApiResponse.success("Đã hủy kết bạn", null));
    }

}
