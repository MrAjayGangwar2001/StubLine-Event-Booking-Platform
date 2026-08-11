package com.eventbooking.repository;

import com.eventbooking.entity.OtpCode;
import com.eventbooking.entity.OtpPurpose;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.Optional;

public interface OtpCodeRepository extends JpaRepository<OtpCode, Long> {

    // Most recent OTP for this email+purpose - used to verify against, and
    // to rate-limit/replace a still-valid unexpired code rather than piling
    // up unlimited active codes per user.
    Optional<OtpCode> findFirstByEmailAndPurposeAndUsedFalseOrderByCreatedAtDesc(String email, OtpPurpose purpose);

    @Modifying
    @Query("DELETE FROM OtpCode o WHERE o.expiresAt < :cutoff")
    void deleteAllExpiredBefore(@Param("cutoff") LocalDateTime cutoff);
}
