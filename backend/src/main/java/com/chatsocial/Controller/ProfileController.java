package com.chatsocial.Controller;

import com.chatsocial.dto.ApiResponse;
import com.chatsocial.dto.profile.*;
import com.chatsocial.enums.ProfileImageType;
import com.chatsocial.service.ProfileService;
import com.chatsocial.utils.SecurityUtils;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequiredArgsConstructor
@RequestMapping(value = "/api/users/me/profile", produces = MediaType.APPLICATION_JSON_VALUE)
@Tag(name = "Profile", description = "Xem, cập nhật hồ sơ và ảnh của tài khoản đang đăng nhập")
public class ProfileController {
    private final ProfileService profiles;

    @GetMapping
    @Operation(summary = "Lấy hồ sơ của tôi")
    public ResponseEntity<ApiResponse<ProfileResponse>> get() {
        return ResponseEntity.ok(ApiResponse.success("Profile retrieved successfully",
                profiles.get(SecurityUtils.getCurrentUserId())));
    }

    @PatchMapping(consumes = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "Cập nhật hồ sơ", description = "Không gửi field: giữ nguyên. Gửi null: xóa, trừ fullName.")
    public ResponseEntity<ApiResponse<ProfileResponse>> update(@Valid @RequestBody UpdateProfileRequest request) {
        return ResponseEntity.ok(ApiResponse.success("Profile updated successfully",
                profiles.update(SecurityUtils.getCurrentUserId(), request)));
    }

    @PostMapping(value = "/avatar", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(summary = "Upload avatar", description = "JPEG, PNG hoặc WebP; tối đa 5 MB. Field multipart: file.")
    public ResponseEntity<ApiResponse<ProfileResponse>> avatar(@RequestPart("file") MultipartFile file) {
        return ResponseEntity.ok(ApiResponse.success("Avatar updated successfully",
                profiles.uploadImage(SecurityUtils.getCurrentUserId(), ProfileImageType.AVATAR, file)));
    }

    @PostMapping(value = "/cover", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(summary = "Upload ảnh cover", description = "JPEG, PNG hoặc WebP; tối đa 5 MB. Field multipart: file.")
    public ResponseEntity<ApiResponse<ProfileResponse>> cover(@RequestPart("file") MultipartFile file) {
        return ResponseEntity.ok(ApiResponse.success("Cover updated successfully",
                profiles.uploadImage(SecurityUtils.getCurrentUserId(), ProfileImageType.COVER, file)));
    }
}
