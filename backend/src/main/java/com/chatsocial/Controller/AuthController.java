package com.chatsocial.Controller;

import com.chatsocial.dto.ApiResponse;
import com.chatsocial.dto.auth.*;
import com.chatsocial.dto.user.UserResponse;
import com.chatsocial.service.AuthService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
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

    @PostMapping("/register")
    @Operation(summary = "Đăng ký tài khoản", description = "Tạo user và profile, sau đó đăng nhập để lấy JWT.")
    public ResponseEntity<ApiResponse<UserResponse>> register(@Valid @RequestBody RegisterRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("Registration successful", authService.register(request)));
    }

    @PostMapping("/login")
    @Operation(summary = "Đăng nhập", description = "Trả accessToken trong data, có thời hạn mặc định 3 tiếng.")
    public ResponseEntity<ApiResponse<AuthResponse>> login(@Valid @RequestBody LoginRequest request) {
        return ResponseEntity.ok(ApiResponse.success("Login successful", authService.login(request)));
    }
}
