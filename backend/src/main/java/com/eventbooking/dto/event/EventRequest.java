package com.eventbooking.dto.event;

import jakarta.validation.constraints.Future;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.time.LocalDateTime;

@Data
public class EventRequest {

    @NotBlank(message = "Title is required")
    private String title;

    private String description;

    @NotBlank(message = "Category is required")
    private String category;

    @NotNull(message = "Venue is required")
    private Long venueId;

    @NotNull(message = "Event date is required")
    @Future(message = "Event date must be in the future")
    private LocalDateTime eventDate;

    // Seat pricing is set separately via
    // POST /api/events/{id}/seats/generate (see EventSeatGenerationRequest),
    // as its own explicit admin step after the event itself is created -
    // NOT here. Do not add goldPrice/silverPrice/platinumPrice fields back
    // to this DTO; the Admin.jsx "Event & Pricing" tab deliberately splits
    // event creation from pricing into two separate form submissions, and
    // EventService.createEvent() has no code path that would use them anyway.
}
