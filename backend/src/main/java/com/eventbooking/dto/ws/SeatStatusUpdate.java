package com.eventbooking.dto.ws;

import com.eventbooking.entity.SeatStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Pushed over WebSocket to /topic/event/{eventId} whenever a seat's status
 * changes, so every connected client's seat map updates live without polling.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SeatStatusUpdate {
    private Long eventSeatId;
    private SeatStatus status;
    private Integer lockTtlSeconds; // populated only when status == LOCKED, so the UI can show a countdown
}
