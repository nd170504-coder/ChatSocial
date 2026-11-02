package com.chatsocial.Controller;

import com.chatsocial.dto.ApiResponse;
import com.chatsocial.dto.auth.*;
import com.chatsocial.dto.user.UserResponse;
import com.chatsocial.service.AuthService;
import com.chatsocial.service.UserSessionService;
import com.chatsocial.utils.SecurityUtils;
import com.chatsocial.config.OpenApiConfig;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
@RequestMapping(value = "/api/auth", produces = MediaType.APPLICATION_JSON_VALUE)
@Tag(name = "Authentication", description = "Đăng ký tài khoản và đăng nhập JWT")
@SecurityRequirements
public class AuthController {
    private final AuthService authService;
    private final UserSessionService sessions;

    @PostMapping("/register")
    @Operation(summary = "Đăng ký tài khoản", description = "Tạo user và profile, sau đó đăng nhập để lấy JWT.")
    public ResponseEntity<ApiResponse<UserResponse>> register(@Valid @RequestBody RegisterRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("Registration successful", authService.register(request)));
    }

    @PostMapping("/login")
    @Operation(summary = "Đăng nhập", description = "Trả accessToken trong data, có thời hạn mặc định 3 tiếng.")
    public ResponseEntity<ApiResponse<AuthResponse>> login(@Valid @RequestBody LoginRequest request,
            @Parameter(hidden = true) @RequestHeader(value = "User-Agent", required = false) String userAgent) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .body(ApiResponse.success("Login successful", authService.login(request, userAgent)));
    }

    @PostMapping("/refresh")
    @Operation(summary = "Làm mới token", description = "Dùng refreshToken một lần để nhận cặp token mới. Không cần access token.")
    public ResponseEntity<ApiResponse<AuthResponse>> refresh(@Valid @RequestBody RefreshTokenRequest request) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .body(ApiResponse.success("Token refreshed successfully", authService.refresh(request)));
    }

    @PostMapping("/logout")
    @SecurityRequirements(@SecurityRequirement(name = OpenApiConfig.BEARER_AUTH))
    @Operation(summary = "Đăng xuất phiên hiện tại", description = "Thu hồi session, access token và refresh token của phiên này.")
    public ResponseEntity<ApiResponse<Void>> logout() {
        sessions.logout(SecurityUtils.getCurrentUserId(), SecurityUtils.getCurrentSessionId());
        return ResponseEntity.ok(ApiResponse.success("Logout successful", null));
    }
}
