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
public class AnalyticsOverviewResponse {
    private BigDecimal totalRevenue;
    private long totalConfirmedBookings;
    private long totalPendingBookings;
    private long totalEvents;
    private long upcomingEvents;
    private double overallOccupancyPercent;
}
