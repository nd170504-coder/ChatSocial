package com.chatsocial.Controller;

import com.chatsocial.dto.ApiResponse;
import com.chatsocial.dto.post.PostPageResponse;
import com.chatsocial.dto.user.AccountActionRequest;
import com.chatsocial.dto.user.UserResponse;
import com.chatsocial.service.AccountService;
import com.chatsocial.service.AuthService;
import com.chatsocial.service.PostService;
import com.chatsocial.utils.SecurityUtils;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
@RequestMapping(value = "/api/users", produces = MediaType.APPLICATION_JSON_VALUE)
@Tag(name = "Users", description = "Thông tin người dùng")
public class UserController {

    private final AuthService authService;
    private final AccountService accountService;
    private final PostService postService;

    @GetMapping("/me")
    @Operation(summary = "Lấy thông tin tài khoản hiện tại")
    public ResponseEntity<ApiResponse<UserResponse>> me() {
        String userId = SecurityUtils.getCurrentUserId();
        return ResponseEntity.ok(ApiResponse.success("User retrieved successfully", authService.me(userId)));
    }

    @PostMapping("/me/deactivate")
    @Operation(summary = "Vô hiệu hóa tài khoản của mình")
    public ResponseEntity<ApiResponse<Void>> deactivate(@Valid @RequestBody AccountActionRequest request) {
        accountService.deactivate(SecurityUtils.getCurrentUserId(), request);
        return ResponseEntity.ok(ApiResponse.success("Account deactivated successfully", null));
    }

    @PostMapping("/me/delete")
    @Operation(summary = "Xóa mềm tài khoản của mình")
    public ResponseEntity<ApiResponse<Void>> deleteAccount(@Valid @RequestBody AccountActionRequest request) {
        accountService.deleteAccount(SecurityUtils.getCurrentUserId(), request);
        return ResponseEntity.ok(ApiResponse.success("Account deleted successfully", null));
    }

    @GetMapping("/{userId}/posts")
    @Operation(
            summary = "Lấy bài viết trên trang cá nhân",
            description = "Lọc theo quyền của người xem. "
                    + "Sắp xếp mới nhất trước. page bắt đầu từ 0, size tối đa 50."
    )
    public ResponseEntity<ApiResponse<PostPageResponse>> getPosts(
            @PathVariable("userId") String userId,
            @RequestParam(name = "page", defaultValue = "0") int page,
            @RequestParam(name = "size", defaultValue = "20") int size
    ) {
        String currentUserId = SecurityUtils.getCurrentUserId();

        PostPageResponse response = postService.getMyPostByUser(currentUserId, userId, page, size);

        return ResponseEntity.ok().body(ApiResponse.success("Posts retrieved successfully", response));
    }

}
