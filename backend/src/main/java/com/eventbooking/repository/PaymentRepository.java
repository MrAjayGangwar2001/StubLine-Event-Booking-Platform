package com.eventbooking.repository;

import com.eventbooking.entity.Payment;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface PaymentRepository extends JpaRepository<Payment, Long> {
    Optional<Payment> findByIdempotencyKey(String idempotencyKey);
    Optional<Payment> findByBookingId(Long bookingId);
    Optional<Payment> findByProviderOrderId(String providerOrderId);
}
