package com.eventbooking.service;

import com.eventbooking.entity.OtpCode;
import com.eventbooking.entity.OtpPurpose;
import com.eventbooking.exception.BadRequestException;
import com.eventbooking.repository.OtpCodeRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.LocalDateTime;

/**
 * A 6-digit numeric code, valid for 10 minutes, single use. Rate-limited via
 * OtpRateLimitService (120s resend cooldown, max 5 sends / 5 wrong-verify
 * attempts before a 6-hour lockout) - see that class for the exact rules.
 */
@Service
@RequiredArgsConstructor
public class OtpService {

    private static final int OTP_LENGTH = 6;
    private static final long OTP_VALIDITY_MINUTES = 10;
    private static final SecureRandom RANDOM = new SecureRandom();

    private final OtpCodeRepository otpCodeRepository;
    private final EmailService emailService;
    private final OtpRateLimitService otpRateLimitService;

    @Transactional
    public void generateAndSendOtp(String email, OtpPurpose purpose) {
        otpRateLimitService.assertCanSend(email, purpose);

        String code = generateNumericCode();

        OtpCode otp = OtpCode.builder()
                .email(email)
                .code(code)
                .purpose(purpose)
                .expiresAt(LocalDateTime.now().plusMinutes(OTP_VALIDITY_MINUTES))
                .used(false)
                .build();
        otpCodeRepository.save(otp);

        emailService.sendOtpEmail(email, code, purpose);

        // Only counted once the email genuinely went out - if sendOtpEmail
        // threw, we don't want to burn one of the person's 5 attempts on a
        // send that never actually reached them.
        otpRateLimitService.recordSend(email, purpose);
    }

    /**
     * Verifies a code and marks it used in one step - a code can only ever
     * be successfully verified once, closing the obvious replay window
     * where someone could reuse an intercepted code multiple times.
     */
    @Transactional
    public void verifyOtp(String email, String submittedCode, OtpPurpose purpose) {
        otpRateLimitService.assertCanVerify(email, purpose);

        OtpCode otp = otpCodeRepository
                .findFirstByEmailAndPurposeAndUsedFalseOrderByCreatedAtDesc(email, purpose)
                .orElseThrow(() -> new BadRequestException("No pending verification code for this email. Please request a new one."));

        if (otp.isUsed()) {
            throw new BadRequestException("This code has already been used. Please request a new one.");
        }

        if (otp.getExpiresAt().isBefore(LocalDateTime.now())) {
            throw new BadRequestException("This code has expired. Please request a new one.");
        }

        if (!otp.getCode().equals(submittedCode)) {
            otpRateLimitService.recordFailedVerification(email, purpose);
            throw new BadRequestException("Incorrect code. Please try again.");
        }

        otp.setUsed(true);
        otpCodeRepository.save(otp);
        otpRateLimitService.recordSuccessfulVerification(email, purpose);
    }

    private String generateNumericCode() {
        int bound = (int) Math.pow(10, OTP_LENGTH);
        int value = RANDOM.nextInt(bound);
        return String.format("%0" + OTP_LENGTH + "d", value);
    }
}
