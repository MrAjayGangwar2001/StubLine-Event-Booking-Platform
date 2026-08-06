package com.eventbooking.controller;

import com.eventbooking.dto.lock.LockResponse;
import com.eventbooking.entity.Event;
import com.eventbooking.entity.User;
import com.eventbooking.service.EventService;
import com.eventbooking.service.SeatLockService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/events/{eventId}/seats/{eventSeatId}/lock")
@RequiredArgsConstructor
public class SeatLockController {

    private final SeatLockService seatLockService;
    private final EventService eventService;
    private static final int LOCK_TTL_SECONDS = 300; // kept in sync with SeatLockService.LOCK_TTL

    /**
     * A user clicks a seat on the map -> frontend calls this before showing
     * it as "selected". If another user already holds the seat, this returns
     * locked=false immediately (no DB round-trip at all).
     */
    @PostMapping
    public ResponseEntity<LockResponse> lockSeat(@PathVariable Long eventId, @PathVariable Long eventSeatId) {
        Event event = eventService.findEventOrThrow(eventId);
        if (!event.isBookable()) {
            return ResponseEntity.ok(LockResponse.builder()
                    .locked(false)
                    .message(bookingUnavailableMessage(event))
                    .build());
        }

        Long userId = getCurrentUserId();
        boolean acquired = seatLockService.tryLock(eventId, eventSeatId, userId);

        LockResponse response = LockResponse.builder()
                .locked(acquired)
                .ttlSeconds(acquired ? LOCK_TTL_SECONDS : null)
                .message(acquired ? "Seat locked for checkout" : "Seat is currently held by another user")
                .build();

        return ResponseEntity.ok(response);
    }

    private String bookingUnavailableMessage(Event event) {
        if (event.getStatus() == com.eventbooking.entity.EventStatus.CANCELLED) {
            return "This event has been cancelled and is no longer bookable";
        }
        return "Booking is currently paused for this event. Please check back later.";
    }

    /**
     * A user deselects a seat, or navigates away, before completing checkout.
     * Frees the seat immediately instead of making everyone else wait out the TTL.
     */
    @DeleteMapping
    public ResponseEntity<LockResponse> unlockSeat(@PathVariable Long eventId, @PathVariable Long eventSeatId) {
        Long userId = getCurrentUserId();
        boolean released = seatLockService.release(eventId, eventSeatId, userId);

        return ResponseEntity.ok(LockResponse.builder()
                .locked(false)
                .message(released ? "Seat released" : "You did not hold this lock")
                .build());
    }

    private Long getCurrentUserId() {
        User user = (User) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        return user.getId();
    }
}
