package com.eventbooking.controller;

import com.eventbooking.dto.event.CancelEventRequest;
import com.eventbooking.dto.event.EventRequest;
import com.eventbooking.dto.event.EventResponse;
import com.eventbooking.dto.event.PostponeEventRequest;
import com.eventbooking.service.EventService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;

@RestController
@RequestMapping("/api/events")
@RequiredArgsConstructor
public class EventController {

    private final EventService eventService;

    @PreAuthorize("hasRole('ADMIN')")
    @PostMapping
    public ResponseEntity<EventResponse> createEvent(@Valid @RequestBody EventRequest request) {
        return ResponseEntity.ok(eventService.createEvent(request));
    }

    @GetMapping
    public ResponseEntity<List<EventResponse>> getAllEvents(
            @RequestParam(required = false) String category) {
        if (category != null && !category.isBlank()) {
            return ResponseEntity.ok(eventService.getEventsByCategory(category));
        }
        return ResponseEntity.ok(eventService.getAllUpcomingEvents());
    }

    @GetMapping("/{id}")
    public ResponseEntity<EventResponse> getEvent(@PathVariable Long id) {
        return ResponseEntity.ok(eventService.getEventById(id));
    }

    // Admin "All Events" / history view - returns every event regardless of
    // status (UPCOMING, COMPLETED, CANCELLED), so the admin panel can show
    // which events have expired instead of them disappearing once
    // getAllEvents() filters down to upcoming-only.
    @PreAuthorize("hasRole('ADMIN')")
    @GetMapping("/admin/all")
    public ResponseEntity<List<EventResponse>> getAllEventsForAdmin() {
        return ResponseEntity.ok(eventService.getAllEventsForAdmin());
    }

    // Poster is optional and uploaded as its own step after the event
    // exists, rather than being part of createEvent()'s JSON body - keeps
    // "create an event" simple and lets the frontend skip this entirely
    // when there's no poster.
    @PreAuthorize("hasRole('ADMIN')")
    @PostMapping(value = "/{id}/poster", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<EventResponse> uploadPoster(@PathVariable Long id,
                                                        @RequestParam("file") MultipartFile file) throws IOException {
        return ResponseEntity.ok(eventService.uploadPosterImage(id, file));
    }

    // --- Admin controls: pause/resume bookings, cancel, postpone ---
    // Kept as separate single-purpose endpoints rather than one generic
    // PATCH /events/{id} - each has different validation and side effects
    // (cancel/postpone both email every confirmed booking holder; pause/
    // resume don't), so folding them into one endpoint would just mean
    // branching on a "type" field instead.

    @PreAuthorize("hasRole('ADMIN')")
    @PostMapping("/{id}/pause-booking")
    public ResponseEntity<EventResponse> pauseBooking(@PathVariable Long id) {
        return ResponseEntity.ok(eventService.pauseBooking(id));
    }

    @PreAuthorize("hasRole('ADMIN')")
    @PostMapping("/{id}/resume-booking")
    public ResponseEntity<EventResponse> resumeBooking(@PathVariable Long id) {
        return ResponseEntity.ok(eventService.resumeBooking(id));
    }

    @PreAuthorize("hasRole('ADMIN')")
    @PostMapping("/{id}/cancel")
    public ResponseEntity<EventResponse> cancelEvent(@PathVariable Long id, @Valid @RequestBody CancelEventRequest request) {
        return ResponseEntity.ok(eventService.cancelEvent(id, request.getReason()));
    }

    @PreAuthorize("hasRole('ADMIN')")
    @PostMapping("/{id}/postpone")
    public ResponseEntity<EventResponse> postponeEvent(@PathVariable Long id, @Valid @RequestBody PostponeEventRequest request) {
        return ResponseEntity.ok(eventService.postponeEvent(id, request.getNewEventDate(), request.getNote()));
    }

    // Seat map lives under EventSeatController (/api/events/{id}/seats) so that
    // seat generation (POST .../seats/generate) and seat map reads sit together.
}
