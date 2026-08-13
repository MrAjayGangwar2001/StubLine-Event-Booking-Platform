package com.eventbooking.dto.booking;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.List;

@Data
public class CreateBookingRequest {

    @NotNull(message = "Event id is required")
    private Long eventId;

    @NotEmpty(message = "At least one seat must be selected")
    private List<Long> eventSeatIds;
}
