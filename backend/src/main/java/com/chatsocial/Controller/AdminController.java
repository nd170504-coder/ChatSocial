package com.chatsocial.Controller;

import com.chatsocial.dto.ApiResponse;
import com.chatsocial.dto.user.AdminUserResponse;
import com.chatsocial.service.AdminService;
import com.chatsocial.utils.SecurityUtils;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/admin")
@RequiredArgsConstructor
public class AdminController {
    private final AdminService admin;

    public record StatusRequest(@NotNull Boolean suspended, @NotBlank @Size(max = 500) String reason) {}

    @GetMapping("/overview")
    public ApiResponse<AdminService.Overview> overview(@RequestParam(defaultValue = "7") int days) {
        return ApiResponse.success("Đã tải tổng quan.", admin.overview(days));
    }

    @GetMapping("/users")
    public ApiResponse<AdminService.UserPage> list(@RequestParam(defaultValue = "") String q,
                                                 @RequestParam(defaultValue = "0") int page,
                                                 @RequestParam(defaultValue = "20") int size) {
        return ApiResponse.success("Đã tải danh sách tài khoản.", admin.list(q, page, size));
    }

    @PatchMapping("/users/{userId}/status")
    public ApiResponse<AdminUserResponse> change(@PathVariable String userId, @Valid @RequestBody StatusRequest request) {
        return ApiResponse.success("Đã cập nhật trạng thái tài khoản.",
                admin.changeStatus(SecurityUtils.getCurrentUserId(), userId, request.suspended(), request.reason()));
    }
}
