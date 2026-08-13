package com.eventbooking.dto.booking;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import com.eventbooking.entity.BookingStatus;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BookingResponse {
    private Long id;
    private Long eventId;
    private String eventTitle;
    private LocalDateTime eventDate;
    private BigDecimal totalAmount;
    private BookingStatus status;
    private List<String> seatLabels; // e.g. ["A5", "A6"]
    private LocalDateTime createdAt;
    private String paymentStatus;       // Payment.status, e.g. "SUCCESS" - null if somehow no Payment row exists
    private String razorpayPaymentId;   // null for a free (₹0) booking - there was never a real gateway payment to reference
}
