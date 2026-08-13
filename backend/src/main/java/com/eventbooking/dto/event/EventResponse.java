package com.eventbooking.dto.event;

import com.eventbooking.entity.EventStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class EventResponse {
    private Long id;
    private String title;
    private String description;
    private String category;
    private LocalDateTime eventDate;
    private EventStatus status;
    private Boolean bookingEnabled;
    private String cancellationReason; // null unless status=CANCELLED
    private String posterImageUrl; // null if no poster was uploaded
    private VenueResponse venue;
}
