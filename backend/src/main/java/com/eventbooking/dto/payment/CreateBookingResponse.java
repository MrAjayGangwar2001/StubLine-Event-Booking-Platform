package com.eventbooking.dto.payment;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/**
 * Returned from POST /api/bookings. The booking exists in the DB now
 * (status=PENDING) but is NOT confirmed yet - the frontend uses these fields
 * to open Razorpay Checkout, and only a successful POST /verify-payment
 * afterwards actually confirms it and marks the seats BOOKED.
 *
 * Exception: a free booking (amount 0) has paymentRequired=false and is
 * ALREADY confirmed by the time this response is returned - there's no
 * order to check out with, so razorpayOrderId/razorpayKeyId are null and
 * the frontend should skip Razorpay Checkout entirely.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CreateBookingResponse {
    private Long bookingId;
    private String razorpayOrderId;
    private String razorpayKeyId; // public key - safe to expose to the frontend, unlike the secret
    private BigDecimal amount;
    private String currency;
    @Builder.Default
    private boolean paymentRequired = true;
}
