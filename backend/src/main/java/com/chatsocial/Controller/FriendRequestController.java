package com.chatsocial.Controller;

import com.chatsocial.dto.ApiResponse;
import com.chatsocial.dto.friend.FriendRequestResponse;
import com.chatsocial.dto.friend.SendFriendRequest;
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

    @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "Gửi lời mời kết bạn", description = "userId là người nhận. Trả 409 nếu đã là bạn hoặc đã có lời mời đang chờ ở một trong hai chiều.")
    public ResponseEntity<ApiResponse<FriendRequestResponse>> send(@Valid @RequestBody SendFriendRequest input) {
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success("Friend request sent successfully",
                requests.send(SecurityUtils.getCurrentUserId(), input)));
    }
}
