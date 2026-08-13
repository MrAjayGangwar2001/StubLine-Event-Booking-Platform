package com.eventbooking.dto.event;

import com.eventbooking.entity.SeatTier;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SeatResponse {
    private Long id;
    private String rowLabel;
    private Integer seatNumber;
    private SeatTier tier;
}
