package com.eventbooking.dto.event;

import jakarta.validation.constraints.Future;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.time.LocalDateTime;

@Data
public class PostponeEventRequest {

    @NotNull(message = "A new event date is required")
    @Future(message = "The new event date must be in the future")
    private LocalDateTime newEventDate;

    // Optional - e.g. "Rescheduled due to venue availability". Shown to
    // everyone who already booked, alongside the new date.
    private String note;
}
