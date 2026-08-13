package com.eventbooking.dto.announcement;

import com.eventbooking.entity.AnnouncementSeverity;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class AnnouncementRequest {

    @NotBlank(message = "Message is required")
    @Size(max = 500, message = "Message must be under 500 characters")
    private String message;

    @NotNull(message = "Severity is required")
    private AnnouncementSeverity severity;
}
