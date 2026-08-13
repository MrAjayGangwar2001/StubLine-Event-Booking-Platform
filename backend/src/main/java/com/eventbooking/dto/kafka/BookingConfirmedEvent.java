package com.eventbooking.dto.kafka;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Published to the "booking-confirmed" Kafka topic once payment is verified.
 * Kept as a flat, self-contained payload (no lazy JPA entities, no nested
 * object graphs) so the consumer never needs a DB round-trip just to read
 * what it was told - and so it stays trivially JSON-serializable.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BookingConfirmedEvent {
    private Long bookingId;
    private String userEmail;
    private String userName;
    private String eventTitle;
    private LocalDateTime eventDate;
    private List<String> seatLabels;
    private BigDecimal totalAmount;
}
