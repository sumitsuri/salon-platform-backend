package com.salonplatform.controller;

import com.salonplatform.dto.ApiResponse;
import com.salonplatform.dto.auth.AuthResponse;
import com.salonplatform.dto.auth.ForgotPasswordRequest;
import com.salonplatform.dto.auth.LoginRequest;
import com.salonplatform.dto.auth.MessageResponse;
import com.salonplatform.dto.auth.LogoutRequest;
import com.salonplatform.dto.auth.RefreshTokenRequest;
import com.salonplatform.session.ClientIpResolver;
import jakarta.servlet.http.HttpServletRequest;
import com.salonplatform.dto.auth.ResetPasswordRequest;
import com.salonplatform.security.SecurityUtils;
import com.salonplatform.service.AuthService;
import com.salonplatform.service.PasswordResetService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;
    private final PasswordResetService passwordResetService;
    private final ClientIpResolver clientIpResolver;

    @PostMapping("/login")
    public ApiResponse<AuthResponse> login(
            @Valid @RequestBody LoginRequest request, HttpServletRequest http) {
        return ApiResponse.ok(authService.login(request, clientIpResolver.resolve(http, request.getPortal())));
    }

    @PostMapping("/refresh")
    public ApiResponse<AuthResponse> refresh(
            @Valid @RequestBody RefreshTokenRequest request, HttpServletRequest http) {
        return ApiResponse.ok(authService.refresh(request.getRefreshToken(), clientIpResolver.resolve(http, null)));
    }

    /** Signs this device out for good. Idempotent: an unknown or already-ended token is not an error. */
    @PostMapping("/logout")
    public ApiResponse<MessageResponse> logout(@RequestBody(required = false) LogoutRequest request) {
        authService.logout(request != null ? request.getRefreshToken() : null);
        return ApiResponse.ok(new MessageResponse("Signed out."));
    }

    @GetMapping("/me")
    public ApiResponse<AuthResponse> me() {
        return ApiResponse.ok(authService.me(SecurityUtils.currentUser()));
    }

    @PostMapping("/forgot-password")
    public ApiResponse<MessageResponse> forgotPassword(@Valid @RequestBody ForgotPasswordRequest request) {
        passwordResetService.requestReset(request.getEmail());
        return ApiResponse.ok(new MessageResponse(
                "If an account exists for that email, a reset link has been sent."));
    }

    @PostMapping("/reset-password")
    public ApiResponse<MessageResponse> resetPassword(@Valid @RequestBody ResetPasswordRequest request) {
        passwordResetService.resetPassword(request.getToken(), request.getPassword());
        return ApiResponse.ok(new MessageResponse("Password updated. You can sign in now."));
    }
}
