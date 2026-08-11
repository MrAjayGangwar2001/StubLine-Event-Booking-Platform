package com.eventbooking.repository;

import com.eventbooking.entity.OtpPurpose;
import com.eventbooking.entity.OtpRateLimit;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface OtpRateLimitRepository extends JpaRepository<OtpRateLimit, Long> {
    Optional<OtpRateLimit> findByEmailAndPurpose(String email, OtpPurpose purpose);
}
