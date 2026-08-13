package com.eventbooking.dto.payment;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class VerifyPaymentRequest {

    @NotNull(message = "Booking id is required")
    private Long bookingId;

    @NotBlank(message = "Razorpay order id is required")
    private String razorpayOrderId;

    @NotBlank(message = "Razorpay payment id is required")
    private String razorpayPaymentId;

    @NotBlank(message = "Razorpay signature is required")
    private String razorpaySignature;
}
