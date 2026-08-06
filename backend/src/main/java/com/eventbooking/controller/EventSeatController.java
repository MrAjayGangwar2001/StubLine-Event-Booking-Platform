package com.eventbooking.controller;

import com.eventbooking.dto.event.EventSeatGenerationRequest;
import com.eventbooking.dto.event.SeatMapResponse;
import com.eventbooking.service.EventSeatService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/events/{eventId}/seats")
@RequiredArgsConstructor
public class EventSeatController {

    private final EventSeatService eventSeatService;

    @PreAuthorize("hasRole('ADMIN')")
    @PostMapping("/generate")
    public ResponseEntity<List<SeatMapResponse>> generateEventSeats(@PathVariable Long eventId,
                                                                      @Valid @RequestBody EventSeatGenerationRequest request) {
        return ResponseEntity.ok(eventSeatService.generateEventSeats(eventId, request));
    }

    // Public - this is the seat map the booking page renders
    @GetMapping
    public ResponseEntity<List<SeatMapResponse>> getSeatMap(@PathVariable Long eventId) {
        return ResponseEntity.ok(eventSeatService.getSeatMap(eventId));
    }
}
