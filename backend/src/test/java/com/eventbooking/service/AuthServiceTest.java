package com.eventbooking.service;

import com.eventbooking.dto.auth.LoginRequest;
import com.eventbooking.dto.auth.RegisterRequest;
import com.eventbooking.dto.auth.VerifyOtpRequest;
import com.eventbooking.entity.AuthProvider;
import com.eventbooking.entity.OtpPurpose;
import com.eventbooking.entity.Role;
import com.eventbooking.entity.User;
import com.eventbooking.exception.BadRequestException;
import com.eventbooking.repository.UserRepository;
import com.eventbooking.security.JwtUtil;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Covers the auth-hardening flow: registration no longer hands out a
 * usable session immediately (see RegisterResponse) - an account only
 * becomes loggable-into after its OTP is verified.
 */
@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @Mock private UserRepository userRepository;
    @Mock private PasswordEncoder passwordEncoder;
    @Mock private JwtUtil jwtUtil;
    @Mock private AuthenticationManager authenticationManager;
    @Mock private OtpService otpService;
    @Mock private GoogleTokenVerifierService googleTokenVerifierService;

    @InjectMocks
    private AuthService authService;

    private RegisterRequest registerRequest;

    @BeforeEach
    void setUp() {
        registerRequest = new RegisterRequest();
        registerRequest.setName("Ajay Gangwar");
        registerRequest.setEmail("ajay@example.com");
        registerRequest.setPassword("password123");
    }

    @Test
    void register_createsUnverifiedUser_andSendsOtp_withoutIssuingAToken() {
        when(userRepository.existsByEmail(registerRequest.getEmail())).thenReturn(false);
        when(passwordEncoder.encode(registerRequest.getPassword())).thenReturn("hashed-password");
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));

        var response = authService.register(registerRequest);

        assertThat(response.getEmail()).isEqualTo("ajay@example.com");
        verify(userRepository).save(argThat(user ->
                user.getRole() == Role.USER
                        && !user.isEmailVerified()
                        && user.getAuthProvider() == AuthProvider.LOCAL));
        verify(otpService).generateAndSendOtp("ajay@example.com", OtpPurpose.SIGNUP_VERIFICATION);
        // No token should ever be generated at registration time - the account isn't usable yet.
        verify(jwtUtil, never()).generateToken(any());
    }

    @Test
    void register_throwsBadRequest_whenEmailAlreadyExists() {
        when(userRepository.existsByEmail(registerRequest.getEmail())).thenReturn(true);

        assertThatThrownBy(() -> authService.register(registerRequest))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("already exists");

        verify(userRepository, never()).save(any());
        verify(otpService, never()).generateAndSendOtp(any(), any());
    }

    @Test
    void verifySignupOtp_marksUserVerified_andIssuesToken() {
        User unverifiedUser = User.builder()
                .id(1L).name("Ajay").email("ajay@example.com")
                .role(Role.USER).emailVerified(false).authProvider(AuthProvider.LOCAL)
                .build();

        VerifyOtpRequest request = new VerifyOtpRequest();
        request.setEmail("ajay@example.com");
        request.setCode("123456");

        when(userRepository.findByEmail("ajay@example.com")).thenReturn(Optional.of(unverifiedUser));
        when(jwtUtil.generateToken(any(User.class))).thenReturn("fake-jwt-token");
        when(userRepository.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));

        var response = authService.verifySignupOtp(request);

        assertThat(response.getToken()).isEqualTo("fake-jwt-token");
        verify(otpService).verifyOtp("ajay@example.com", "123456", OtpPurpose.SIGNUP_VERIFICATION);
        assertThat(unverifiedUser.isEmailVerified()).isTrue();
    }

    @Test
    void login_rejectsWithClearMessage_whenAccountNotYetVerified() {
        User unverifiedUser = User.builder()
                .id(1L).name("Ajay").email("ajay@example.com")
                .passwordHash("hashed").role(Role.USER)
                .emailVerified(false).authProvider(AuthProvider.LOCAL)
                .build();

        LoginRequest request = new LoginRequest();
        request.setEmail("ajay@example.com");
        request.setPassword("password123");

        when(userRepository.findByEmail("ajay@example.com")).thenReturn(Optional.of(unverifiedUser));
        when(authenticationManager.authenticate(any())).thenThrow(new DisabledException("disabled"));

        assertThatThrownBy(() -> authService.login(request))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("verify your email");
    }

    @Test
    void login_rejectsGoogleAccounts_beforeEvenAttemptingPasswordAuth() {
        User googleUser = User.builder()
                .id(1L).name("Ajay").email("ajay@example.com")
                .passwordHash(null).role(Role.USER)
                .emailVerified(true).authProvider(AuthProvider.GOOGLE)
                .build();

        LoginRequest request = new LoginRequest();
        request.setEmail("ajay@example.com");
        request.setPassword("anything");

        when(userRepository.findByEmail("ajay@example.com")).thenReturn(Optional.of(googleUser));

        assertThatThrownBy(() -> authService.login(request))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("Google");

        verify(authenticationManager, never()).authenticate(any());
    }

    @Test
    void googleLogin_createsNewVerifiedAccount_whenEmailNotSeenBefore() {
        when(googleTokenVerifierService.verify("valid-credential"))
                .thenReturn(new GoogleTokenVerifierService.GoogleUserInfo("newuser@gmail.com", "New User"));
        when(userRepository.findByEmail("newuser@gmail.com")).thenReturn(Optional.empty());
        when(userRepository.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));
        when(jwtUtil.generateToken(any(User.class))).thenReturn("fake-jwt-token");

        com.eventbooking.dto.auth.GoogleLoginRequest request = new com.eventbooking.dto.auth.GoogleLoginRequest();
        request.setCredential("valid-credential");

        var response = authService.googleLogin(request);

        assertThat(response.getToken()).isEqualTo("fake-jwt-token");
        verify(userRepository).save(argThat(user ->
                user.isEmailVerified()
                        && user.getAuthProvider() == AuthProvider.GOOGLE
                        && user.getPasswordHash() == null));
    }
}
