package com.chatsocial.Controller;

import com.chatsocial.dto.ApiResponse;
import com.chatsocial.dto.auth.*;
import com.chatsocial.dto.user.UserResponse;
import com.chatsocial.service.AuthService;
import com.chatsocial.service.PasswordService;
import com.chatsocial.service.UserSessionService;
import com.chatsocial.service.EmailVerificationService;
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
    private final PasswordService passwordService;
    private final UserSessionService sessions;
    private final EmailVerificationService verification;

    @PostMapping("/register")
    @Operation(summary = "Đăng ký tài khoản", description = "Tạo tài khoản chờ xác minh và gửi OTP qua email. Xác minh trước khi login.")
    public ResponseEntity<ApiResponse<UserResponse>> register(@Valid @RequestBody RegisterRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("Registration received; check your email for verification", authService.register(request)));
    }

    @PostMapping("/verify-email")
    @Operation(summary = "Xác minh email", description = "Nhập email và OTP 6 chữ số. Thành công thì có thể login.")
    public ResponseEntity<ApiResponse<UserResponse>> verifyEmail(@Valid @RequestBody VerifyEmailRequest request) {
        return ResponseEntity.ok(ApiResponse.success("Email verified successfully", verification.verify(request)));
    }

    @PostMapping("/resend-verification")
    @Operation(summary = "Gửi lại OTP", description = "Tối thiểu 60 giây giữa các lần gửi, tối đa 5 email mỗi giờ cho một tài khoản.")
    public ResponseEntity<ApiResponse<Void>> resendVerification(@Valid @RequestBody ResendVerificationRequest request) {
        verification.resend(request);
        return ResponseEntity.accepted().body(ApiResponse.success(
                "If the account is pending verification and sending is allowed, a code will be emailed", null));
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

    @PostMapping("/change-password")
    @SecurityRequirements(@SecurityRequirement(name = OpenApiConfig.BEARER_AUTH))
    @Operation(summary = "Đổi mật khẩu", description = "Kiểm tra mật khẩu hiện tại, đổi mật khẩu và thu hồi toàn bộ phiên đăng nhập.")
    public ResponseEntity<ApiResponse<Void>> changePassword(@Valid @RequestBody ChangePasswordRequest request) {
        passwordService.changePassword(SecurityUtils.getCurrentUserId(), request);
        return ResponseEntity.ok(ApiResponse.success("Password changed successfully; please log in again", null));
    }

    @PostMapping("/forgot-password")
    @Operation(summary = "Quên mật khẩu", description = "Gửi OTP qua email. Dùng lại API này để gửi lại mã, áp dụng giới hạn như OTP xác minh email.")
    public ResponseEntity<ApiResponse<Void>> forgotPassword(@Valid @RequestBody ForgotPasswordRequest request) {
        passwordService.forgotPassword(request);
        return ResponseEntity.accepted().body(ApiResponse.success(
                "If the account is eligible and sending is allowed, a password reset code will be emailed", null));
    }

    @PostMapping("/reset-password")
    @Operation(summary = "Đặt lại mật khẩu", description = "Dùng email và OTP để đặt mật khẩu mới, thu hồi toàn bộ phiên đăng nhập.")
    public ResponseEntity<ApiResponse<Void>> resetPassword(@Valid @RequestBody ResetPasswordRequest request) {
        passwordService.resetPassword(request);
        return ResponseEntity.ok(ApiResponse.success("Password reset successfully; please log in again", null));
    }

    @PostMapping("/logout")
    @SecurityRequirements(@SecurityRequirement(name = OpenApiConfig.BEARER_AUTH))
    @Operation(summary = "Đăng xuất phiên hiện tại", description = "Thu hồi session, access token và refresh token của phiên này.")
    public ResponseEntity<ApiResponse<Void>> logout() {
        sessions.logout(SecurityUtils.getCurrentUserId(), SecurityUtils.getCurrentSessionId());
        return ResponseEntity.ok(ApiResponse.success("Logout successful", null));
    }
}
