package com.eventbooking.dto.event;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.math.BigDecimal;
import java.util.Map;

/**
 * Maps each seat tier to a price for a specific event, used to initialize
 * EventSeat rows (one per physical Seat) when an event goes on sale.
 */
@Data
public class EventSeatGenerationRequest {

    @NotEmpty(message = "At least one tier price is required")
    private Map<String, BigDecimal> tierPrices; // e.g. {"GOLD": 1500, "SILVER": 800, "PLATINUM": 3000}
}
