package com.eventbooking.dto.event;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class CancelEventRequest {

    @NotBlank(message = "A reason is required - it's shown to everyone who booked this event")
    private String reason;
}
