package com.eventbooking.controller;

import com.eventbooking.dto.event.SeatGenerationRequest;
import com.eventbooking.dto.event.SeatResponse;
import com.eventbooking.service.SeatService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/venues/{venueId}/seats")
@RequiredArgsConstructor
public class SeatController {

    private final SeatService seatService;

    @PreAuthorize("hasRole('ADMIN')")
    @PostMapping("/generate")
    public ResponseEntity<List<SeatResponse>> generateSeats(@PathVariable Long venueId,
                                                      @Valid @RequestBody SeatGenerationRequest request) {
        return ResponseEntity.ok(seatService.generateSeatsForVenue(venueId, request));
    }

    @GetMapping
    public ResponseEntity<List<SeatResponse>> getVenueSeats(@PathVariable Long venueId) {
        return ResponseEntity.ok(seatService.getSeatsForVenue(venueId));
    }
}
