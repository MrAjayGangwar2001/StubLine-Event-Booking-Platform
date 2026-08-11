package com.eventbooking.service;

import com.eventbooking.repository.OtpCodeRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

/**
 * Pure hygiene - expired OTP codes are already functionally unusable
 * (OtpService checks expiresAt on every verify attempt regardless), this
 * just keeps the table from growing unbounded forever.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class OtpCleanupJob {

    private final OtpCodeRepository otpCodeRepository;

    @Scheduled(fixedRate = 3_600_000) // every hour
    @Transactional
    public void deleteExpiredCodes() {
        otpCodeRepository.deleteAllExpiredBefore(LocalDateTime.now());
    }
}
