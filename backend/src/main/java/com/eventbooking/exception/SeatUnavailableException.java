package com.eventbooking.exception;

/**
 * Thrown when a seat lock cannot be acquired (already held by another user)
 * or when a seat is no longer AVAILABLE at booking time. Used from Week 3 onward.
 */
public class SeatUnavailableException extends RuntimeException {
    public SeatUnavailableException(String message) {
        super(message);
    }
}
