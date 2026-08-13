package com.eventbooking.dto.event;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.Data;

@Data
public class VenueRequest {

    @NotBlank(message = "Venue name is required")
    private String name;

    @NotBlank(message = "Address is required")
    private String address;

    @NotBlank(message = "City is required")
    private String city;

    @NotNull(message = "Total capacity is required")
    @Positive(message = "Total capacity must be positive")
    private Integer totalCapacity;

    // Seat layout is generated separately via
    // POST /api/venues/{id}/seats/generate (see SeatGenerationRequest),
    // so a venue can exist before its seat map is finalized.
}
