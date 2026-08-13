package com.eventbooking.dto.event;

import com.eventbooking.entity.SeatStatus;
import com.eventbooking.entity.SeatTier;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SeatMapResponse {
    private Long eventSeatId;
    private String rowLabel;
    private Integer seatNumber;
    private SeatTier tier;
    private BigDecimal price;
    private SeatStatus status;
}
