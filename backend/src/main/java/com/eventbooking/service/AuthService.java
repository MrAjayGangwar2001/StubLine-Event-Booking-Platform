package com.eventbooking.service;

import com.eventbooking.dto.auth.*;
import com.eventbooking.entity.AuthProvider;
import com.eventbooking.entity.OtpPurpose;
import com.eventbooking.entity.Role;
import com.eventbooking.entity.User;
import com.eventbooking.exception.BadRequestException;
import com.eventbooking.repository.UserRepository;
import com.eventbooking.security.JwtUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * WEEK 7 (auth hardening): registration no longer hands out a usable account
 * immediately - a new LOCAL account starts with emailVerified=false and
 * genuinely cannot log in (see User.isEnabled()) until they prove they own
 * the email address via a one-time code. Google accounts skip this entirely,
 * since Google has already done that verification on our behalf.
 *
 * SCOPE NOTE, stated plainly rather than glossed over: there is no
 * rate-limiting or brute-force lockout on OTP attempts here (e.g. someone
 * could script guessing a 6-digit code - 1 in a million per attempt, but
 * nothing currently throttles repeated tries). A production deployment
 * would add per-email/per-IP attempt limits before this is safe against a
 * dedicated attacker. Flagging this honestly rather than implying it's
 * fully hardened.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtUtil jwtUtil;
    private final AuthenticationManager authenticationManager;
    private final OtpService otpService;
    private final GoogleTokenVerifierService googleTokenVerifierService;

    @Transactional
    public RegisterResponse register(RegisterRequest request) {
        if (userRepository.existsByEmail(request.getEmail())) {
            throw new BadRequestException("An account with this email already exists");
        }

        User user = User.builder()
                .name(request.getName())
                .email(request.getEmail())
                .passwordHash(passwordEncoder.encode(request.getPassword()))
                .role(Role.USER) // admins are promoted manually / seeded, never self-registered
                .emailVerified(false)
                .authProvider(AuthProvider.LOCAL)
                .build();

        userRepository.save(user);
        otpService.generateAndSendOtp(user.getEmail(), OtpPurpose.SIGNUP_VERIFICATION);

        return RegisterResponse.builder()
                .email(user.getEmail())
                .message("We've sent a 6-digit verification code to your email. Enter it to activate your account.")
                .build();
    }

    /**
     * Completes registration: verifies the OTP, flips emailVerified to true,
     * and - only now - issues a JWT, since this is the first moment the
     * account is actually allowed to be logged into.
     */
    @Transactional
    public AuthResponse verifySignupOtp(VerifyOtpRequest request) {
        User user = userRepository.findByEmail(request.getEmail())
                .orElseThrow(() -> new BadRequestException("No account found for this email"));

        if (user.isEmailVerified()) {
            // Already verified (e.g. they clicked an old verify link twice,
            // or retried after a slow response) - log them in rather than
            // erroring on a code that's since been consumed.
            return buildAuthResponse(user);
        }

        otpService.verifyOtp(request.getEmail(), request.getCode(), OtpPurpose.SIGNUP_VERIFICATION);

        user.setEmailVerified(true);
        userRepository.save(user);

        return buildAuthResponse(user);
    }

    public AuthResponse login(LoginRequest request) {
        User user = userRepository.findByEmail(request.getEmail())
                .orElseThrow(() -> new BadRequestException("Invalid email or password"));

        if (user.getPasswordHash() == null) {
            throw new BadRequestException("This account signs in with Google. Use the \"Sign in with Google\" button instead, or set a password via \"Forgot password\".");
        }

        try {
            authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(request.getEmail(), request.getPassword())
            );
        } catch (DisabledException ex) {
            // Thrown by DaoAuthenticationProvider because User.isEnabled()
            // returned false - i.e. emailVerified is still false. Caught
            // specifically so we can say exactly why, instead of a generic
            // "invalid credentials" that would leave a legitimate user stuck
            // with no idea what to do next.
            throw new BadRequestException("Please verify your email before logging in. Check your inbox for the verification code, or request a new one.");
        }

        return buildAuthResponse(user);
    }

    /**
     * First half of OTP-based login: confirms the account exists and is
     * already verified, then sends a fresh code. Deliberately reuses the
     * SIGNUP_VERIFICATION-style flow but under the LOGIN purpose, so a
     * signup code can never be replayed to log in and vice versa.
     *
     * Intentionally allowed for GOOGLE accounts too (previously blocked) -
     * a Google account has no password, so before this the *only* way to
     * log in was through an active Google session on that exact device.
     * Anyone on a new device/browser had no recovery path at all. Proving
     * control of the same verified inbox via a one-time code is an
     * equally valid login factor, Google-provisioned or not.
     */
    public void requestLoginOtp(RequestOtpRequest request) {
        User user = userRepository.findByEmail(request.getEmail())
                .orElseThrow(() -> new BadRequestException("No account found for this email"));

        if (!user.isEmailVerified()) {
            throw new BadRequestException("Please verify your email first (check your inbox for the signup code) before logging in with OTP.");
        }

        otpService.generateAndSendOtp(user.getEmail(), OtpPurpose.LOGIN);
    }

    @Transactional
    public AuthResponse loginWithOtp(VerifyOtpRequest request) {
        User user = userRepository.findByEmail(request.getEmail())
                .orElseThrow(() -> new BadRequestException("No account found for this email"));

        otpService.verifyOtp(request.getEmail(), request.getCode(), OtpPurpose.LOGIN);

        return buildAuthResponse(user);
    }

    /**
     * Finds an existing account by email, or creates a new one - Google
     * accounts are trusted as already-verified (emailVerified=true) since
     * Google itself confirmed the email, and never get a local password at all.
     * If someone previously registered the SAME email with a password
     * (LOCAL), this deliberately links to that existing account rather than
     * creating a duplicate - one email, one account, regardless of how they
     * choose to sign in on a given day.
     */
    @Transactional
    public AuthResponse googleLogin(GoogleLoginRequest request) {
        GoogleTokenVerifierService.GoogleUserInfo googleUser = googleTokenVerifierService.verify(request.getCredential());

        User user = userRepository.findByEmail(googleUser.getEmail())
                .orElseGet(() -> {
                    User newUser = User.builder()
                            .name(googleUser.getName())
                            .email(googleUser.getEmail())
                            .passwordHash(null)
                            .role(Role.USER)
                            .emailVerified(true)
                            .authProvider(AuthProvider.GOOGLE)
                            .build();
                    return userRepository.save(newUser);
                });

        return buildAuthResponse(user);
    }

    /**
     * Deliberately confirms/denies whether an email is registered - normally
     * a password-reset flow would avoid that to prevent email enumeration,
     * but the explicit requirement here was a clear "not registered, please
     * sign up" message rather than a vague "if this email exists..." one.
     * Trade-off noted, not accidental.
     *
     * Intentionally allowed for GOOGLE accounts too (previously blocked).
     * This is the flow that actually lets a Google-signed-up user set a
     * password from ANY device, without needing an active Google session on
     * that device first - resetPassword() below verifies the OTP (proof of
     * email ownership) and sets passwordHash, which is all login() now
     * requires to allow password login regardless of authProvider.
     */
    public void forgotPasswordRequest(ForgotPasswordRequest request) {
        User user = userRepository.findByEmail(request.getEmail())
                .orElseThrow(() -> new BadRequestException("No account is registered with this email. Please register first."));

        otpService.generateAndSendOtp(user.getEmail(), OtpPurpose.PASSWORD_RESET);
    }

    @Transactional
    public void resetPassword(ResetPasswordRequest request) {
        User user = userRepository.findByEmail(request.getEmail())
                .orElseThrow(() -> new BadRequestException("No account is registered with this email."));

        otpService.verifyOtp(request.getEmail(), request.getCode(), OtpPurpose.PASSWORD_RESET);

        user.setPasswordHash(passwordEncoder.encode(request.getNewPassword()));
        // A password reset via a verified email code is itself proof of
        // email ownership - if this account somehow wasn't verified yet
        // (shouldn't normally happen for a LOCAL account that got this far),
        // this closes that gap too rather than leaving them stuck.
        user.setEmailVerified(true);
        userRepository.save(user);
    }

    private AuthResponse buildAuthResponse(User user) {
        String token = jwtUtil.generateToken(user);
        return AuthResponse.builder()
                .token(token)
                .name(user.getName())
                .email(user.getEmail())
                .role(user.getRole().name())
                .build();
    }
}
