package com.eventbooking.exception;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;

@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(BadRequestException.class)
    public ResponseEntity<ErrorResponse> handleBadRequest(BadRequestException ex) {
        return buildResponse(HttpStatus.BAD_REQUEST, ex.getMessage());
    }

    @ExceptionHandler(ResourceNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleNotFound(ResourceNotFoundException ex) {
        return buildResponse(HttpStatus.NOT_FOUND, ex.getMessage());
    }

    @ExceptionHandler(SeatUnavailableException.class)
    public ResponseEntity<ErrorResponse> handleSeatUnavailable(SeatUnavailableException ex) {
        return buildResponse(HttpStatus.CONFLICT, ex.getMessage());
    }

    @ExceptionHandler(BadCredentialsException.class)
    public ResponseEntity<ErrorResponse> handleBadCredentials(BadCredentialsException ex) {
        return buildResponse(HttpStatus.UNAUTHORIZED, "Invalid email or password");
    }

    /**
     * Catches malformed request bodies - most commonly a date/time field
     * that doesn't match the expected format (e.g. "2026-07-24 12:46"
     * instead of "2026-07-24T12:46"). Without this, Jackson's raw parse
     * exception ("JSON parse error: Cannot deserialize value of type
     * java.time.LocalDateTime from String...") fell through to the generic
     * 500 handler below and was shown to the user verbatim - technically
     * accurate, completely unhelpful. The real fix for the postpone-event
     * case specifically was switching the frontend to a native
     * datetime-local picker (guarantees a valid format), but this handler
     * stays as a safety net for any other request body that's malformed
     * for whatever reason.
     */
    @org.springframework.web.bind.annotation.ExceptionHandler(org.springframework.http.converter.HttpMessageNotReadableException.class)
    public ResponseEntity<ErrorResponse> handleMalformedRequest(org.springframework.http.converter.HttpMessageNotReadableException ex) {
        String message = "One of the fields in your request isn't in the format the server expects.";
        Throwable cause = ex.getMostSpecificCause();
        if (cause instanceof java.time.format.DateTimeParseException) {
            message = "That date/time isn't in a valid format. Please use the date picker instead of typing it manually.";
        }
        return buildResponse(HttpStatus.BAD_REQUEST, message);
    }

    /**
     * Safety net: each service method that could hit a unique constraint or a
     * stale @Version write is expected to check for that case explicitly and
     * throw a specific exception instead (see SeatService, EventSeatService,
     * BookingService). These two handlers exist so that if a spot is ever
     * missed, the user still gets a clean 409 instead of a raw 500.
     */
    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ErrorResponse> handleDataIntegrityViolation(DataIntegrityViolationException ex) {
        return buildResponse(HttpStatus.CONFLICT, "This operation conflicts with existing data.");
    }

    @ExceptionHandler(OptimisticLockingFailureException.class)
    public ResponseEntity<ErrorResponse> handleOptimisticLock(OptimisticLockingFailureException ex) {
        return buildResponse(HttpStatus.CONFLICT, "This record was just modified by someone else. Please try again.");
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Map<String, String>> handleValidation(MethodArgumentNotValidException ex) {
        Map<String, String> errors = new LinkedHashMap<>();
        ex.getBindingResult().getFieldErrors().forEach(err ->
                errors.put(err.getField(), err.getDefaultMessage()));
        return ResponseEntity.badRequest().body(errors);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleGeneric(Exception ex) {
        return buildResponse(HttpStatus.INTERNAL_SERVER_ERROR, "Something went wrong: " + ex.getMessage());
    }

    private ResponseEntity<ErrorResponse> buildResponse(HttpStatus status, String message) {
        ErrorResponse body = ErrorResponse.builder()
                .timestamp(LocalDateTime.now())
                .status(status.value())
                .error(status.getReasonPhrase())
                .message(message)
                .build();
        return ResponseEntity.status(status).body(body);
    }
}


