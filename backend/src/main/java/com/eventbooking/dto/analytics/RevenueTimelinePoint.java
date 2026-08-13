package com.eventbooking.dto.analytics;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RevenueTimelinePoint {
    private LocalDate date;
    private long confirmedBookings;
    private BigDecimal revenue;
}
