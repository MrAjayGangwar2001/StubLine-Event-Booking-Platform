package com.eventbooking.dto.analytics;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class EventAnalyticsResponse {
    private Long eventId;
    private String title;
    private BigDecimal revenue;
    private long bookedSeats;
    private long totalSeats;
    private double occupancyPercent;
}
