package com.chatsocial.Controller;

import com.chatsocial.dto.ApiResponse;
import com.chatsocial.dto.friend.FriendRequestResponse;
import com.chatsocial.dto.friend.SendFriendRequest;
import com.chatsocial.dto.friend.FriendRequestPageResponse;
import com.chatsocial.service.FriendRequestService;
import com.chatsocial.utils.SecurityUtils;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
@RequestMapping(value = "/api/friend-requests", produces = MediaType.APPLICATION_JSON_VALUE)
@Tag(name = "Friend requests", description = "Lời mời kết bạn")
public class FriendRequestController {
    private final FriendRequestService requests;

    @PostMapping("/{requestId}/accept")
    @Operation(summary = "Chấp nhận lời mời kết bạn", description = "Chỉ người nhận được chấp nhận lời mời PENDING; tạo quan hệ bạn bè trong cùng transaction.")
    public ResponseEntity<ApiResponse<FriendRequestResponse>> accept(@PathVariable("requestId") String requestId) {
        return ResponseEntity.ok(ApiResponse.success("Friend request accepted successfully",
                requests.respond(SecurityUtils.getCurrentUserId(), requestId, true)));
    }

    @PostMapping("/{requestId}/reject")
    @Operation(summary = "Từ chối lời mời kết bạn", description = "Chỉ người nhận được từ chối lời mời PENDING.")
    public ResponseEntity<ApiResponse<FriendRequestResponse>> reject(@PathVariable("requestId") String requestId) {
        return ResponseEntity.ok(ApiResponse.success("Friend request rejected successfully",
                requests.respond(SecurityUtils.getCurrentUserId(), requestId, false)));
    }

    @GetMapping("/incoming")
    @Operation(summary = "Lời mời kết bạn đã nhận", description = "Chỉ lời mời PENDING của tài khoản đang đăng nhập, mới nhất trước.")
    public ResponseEntity<ApiResponse<FriendRequestPageResponse>> incoming(
            @RequestParam(name = "page", defaultValue = "0") int page,
            @RequestParam(name = "size", defaultValue = "20") int size) {
        return ResponseEntity.ok(ApiResponse.success("Incoming friend requests retrieved successfully",
                requests.list(SecurityUtils.getCurrentUserId(), true, page, size)));
    }

    @GetMapping("/outgoing")
    @Operation(summary = "Lời mời kết bạn đã gửi", description = "Chỉ lời mời PENDING của tài khoản đang đăng nhập, mới nhất trước.")
    public ResponseEntity<ApiResponse<FriendRequestPageResponse>> outgoing(
            @RequestParam(name = "page", defaultValue = "0") int page,
            @RequestParam(name = "size", defaultValue = "20") int size) {
        return ResponseEntity.ok(ApiResponse.success("Outgoing friend requests retrieved successfully",
                requests.list(SecurityUtils.getCurrentUserId(), false, page, size)));
    }

    @DeleteMapping("/{requestId}")
    @Operation(summary = "Hủy lời mời kết bạn", description = "Chỉ người gửi được hủy lời mời PENDING. Trả 404 nếu không tồn tại/không phải người gửi; 409 nếu đã được xử lý.")
    public ResponseEntity<ApiResponse<Void>> cancel(@PathVariable("requestId") String requestId) {
        requests.cancel(SecurityUtils.getCurrentUserId(), requestId);
        return ResponseEntity.ok(ApiResponse.success("Friend request cancelled successfully", null));
    }

    @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "Gửi lời mời kết bạn", description = "userId là người nhận. Trả 409 nếu đã là bạn hoặc đã có lời mời đang chờ ở một trong hai chiều.")
    public ResponseEntity<ApiResponse<FriendRequestResponse>> send(@Valid @RequestBody SendFriendRequest input) {
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success("Friend request sent successfully",
                requests.send(SecurityUtils.getCurrentUserId(), input)));
    }
}
