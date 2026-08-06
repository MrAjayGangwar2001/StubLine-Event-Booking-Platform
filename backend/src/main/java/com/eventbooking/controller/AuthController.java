package com.eventbooking.controller;

import com.eventbooking.dto.auth.*;
import com.eventbooking.service.AuthService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    /**
     * Creates the account but does NOT log them in - see RegisterResponse
     * for why. Frontend should route straight to the OTP-entry screen after this.
     */
    @PostMapping("/register")
    public ResponseEntity<RegisterResponse> register(@Valid @RequestBody RegisterRequest request) {
        return ResponseEntity.ok(authService.register(request));
    }

    @PostMapping("/verify-signup-otp")
    public ResponseEntity<AuthResponse> verifySignupOtp(@Valid @RequestBody VerifyOtpRequest request) {
        return ResponseEntity.ok(authService.verifySignupOtp(request));
    }

    @PostMapping("/login")
    public ResponseEntity<AuthResponse> login(@Valid @RequestBody LoginRequest request) {
        return ResponseEntity.ok(authService.login(request));
    }

    @PostMapping("/login/otp/request")
    public ResponseEntity<SimpleMessageResponse> requestLoginOtp(@Valid @RequestBody RequestOtpRequest request) {
        authService.requestLoginOtp(request);
        return ResponseEntity.ok(SimpleMessageResponse.builder()
                .message("A login code has been sent to your email.")
                .build());
    }

    @PostMapping("/login/otp/verify")
    public ResponseEntity<AuthResponse> loginWithOtp(@Valid @RequestBody VerifyOtpRequest request) {
        return ResponseEntity.ok(authService.loginWithOtp(request));
    }

    @PostMapping("/google")
    public ResponseEntity<AuthResponse> googleLogin(@Valid @RequestBody GoogleLoginRequest request) {
        return ResponseEntity.ok(authService.googleLogin(request));
    }

    @PostMapping("/forgot-password/request")
    public ResponseEntity<SimpleMessageResponse> forgotPasswordRequest(@Valid @RequestBody ForgotPasswordRequest request) {
        authService.forgotPasswordRequest(request);
        return ResponseEntity.ok(SimpleMessageResponse.builder()
                .message("A password reset code has been sent to your email.")
                .build());
    }

    @PostMapping("/forgot-password/reset")
    public ResponseEntity<SimpleMessageResponse> resetPassword(@Valid @RequestBody ResetPasswordRequest request) {
        authService.resetPassword(request);
        return ResponseEntity.ok(SimpleMessageResponse.builder()
                .message("Password updated. You can now log in with your new password.")
                .build());
    }
}
