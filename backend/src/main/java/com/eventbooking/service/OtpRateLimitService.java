package com.eventbooking.service;

import com.eventbooking.entity.OtpPurpose;
import com.eventbooking.entity.OtpRateLimit;
import com.eventbooking.exception.BadRequestException;
import com.eventbooking.repository.OtpRateLimitRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDateTime;

/**
 * Rules (per email+purpose, so a signup-OTP lockout doesn't also block
 * login-OTP attempts for the same address):
 *   - 120 seconds must pass between consecutive sends
 *   - Max 5 sends before a 6-hour lockout kicks in
 *   - Max 5 wrong-code verification attempts before the same 6-hour lockout
 *   - A successful verification resets both counters back to zero
 *   - Once locked, BOTH sending and verifying are blocked until it expires
 *   - Lockout state is checked live against "now" on every call, not via a
 *     scheduled job flipping a flag - see OtpRateLimit's comment
 */
@Service
@RequiredArgsConstructor
public class OtpRateLimitService {

    private static final int MAX_SENDS = 5;
    private static final int MAX_FAILED_ATTEMPTS = 5;
    private static final Duration RESEND_COOLDOWN = Duration.ofSeconds(120);
    private static final Duration LOCKOUT_DURATION = Duration.ofHours(6);

    private final OtpRateLimitRepository otpRateLimitRepository;

    @Transactional
    public void assertCanSend(String email, OtpPurpose purpose) {
        OtpRateLimit limit = getOrCreate(email, purpose);
        resetIfLockExpired(limit);

        if (isLocked(limit)) {
            throw new BadRequestException(lockedMessage(limit));
        }

        if (limit.getLastSentAt() != null) {
            LocalDateTime cooldownEnds = limit.getLastSentAt().plus(RESEND_COOLDOWN);
            if (cooldownEnds.isAfter(LocalDateTime.now())) {
                long secondsLeft = Duration.between(LocalDateTime.now(), cooldownEnds).getSeconds() + 1;
                throw new BadRequestException("Please wait " + secondsLeft + " seconds before requesting another code.");
            }
        }

        if (limit.getSendCount() >= MAX_SENDS) {
            lock(limit);
            throw new BadRequestException(lockedMessage(limit));
        }
    }

    @Transactional
    public void recordSend(String email, OtpPurpose purpose) {
        OtpRateLimit limit = getOrCreate(email, purpose);
        limit.setSendCount(limit.getSendCount() + 1);
        limit.setLastSentAt(LocalDateTime.now());
        otpRateLimitRepository.save(limit);
    }

    @Transactional
    public void assertCanVerify(String email, OtpPurpose purpose) {
        OtpRateLimit limit = getOrCreate(email, purpose);
        resetIfLockExpired(limit);

        if (isLocked(limit)) {
            throw new BadRequestException(lockedMessage(limit));
        }
    }

    @Transactional
    public void recordFailedVerification(String email, OtpPurpose purpose) {
        OtpRateLimit limit = getOrCreate(email, purpose);
        limit.setFailedAttempts(limit.getFailedAttempts() + 1);
        if (limit.getFailedAttempts() >= MAX_FAILED_ATTEMPTS) {
            lock(limit);
        } else {
            otpRateLimitRepository.save(limit);
        }
    }

    @Transactional
    public void recordSuccessfulVerification(String email, OtpPurpose purpose) {
        OtpRateLimit limit = getOrCreate(email, purpose);
        limit.setSendCount(0);
        limit.setFailedAttempts(0);
        limit.setLockedUntil(null);
        otpRateLimitRepository.save(limit);
    }

    private OtpRateLimit getOrCreate(String email, OtpPurpose purpose) {
        return otpRateLimitRepository.findByEmailAndPurpose(email, purpose)
                .orElseGet(() -> OtpRateLimit.builder().email(email).purpose(purpose).build());
    }

    private boolean isLocked(OtpRateLimit limit) {
        return limit.getLockedUntil() != null && limit.getLockedUntil().isAfter(LocalDateTime.now());
    }

    private void lock(OtpRateLimit limit) {
        limit.setLockedUntil(LocalDateTime.now().plus(LOCKOUT_DURATION));
        otpRateLimitRepository.save(limit);
    }

    private void resetIfLockExpired(OtpRateLimit limit) {
        if (limit.getLockedUntil() != null && limit.getLockedUntil().isBefore(LocalDateTime.now())) {
            limit.setSendCount(0);
            limit.setFailedAttempts(0);
            limit.setLockedUntil(null);
        }
    }

    private String lockedMessage(OtpRateLimit limit) {
        long minutesLeft = Duration.between(LocalDateTime.now(), limit.getLockedUntil()).toMinutes() + 1;
        long hoursLeft = (minutesLeft + 59) / 60;
        return "Too many attempts. Please try again in about " + hoursLeft + " hour(s).";
    }
}
