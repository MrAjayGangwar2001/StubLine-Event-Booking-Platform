package com.eventbooking.service;

import com.eventbooking.dto.event.EventRequest;
import com.eventbooking.dto.event.EventResponse;
import com.eventbooking.dto.event.VenueResponse;
import com.eventbooking.entity.Booking;
import com.eventbooking.entity.BookingStatus;
import com.eventbooking.entity.Event;
import com.eventbooking.entity.EventStatus;
import com.eventbooking.entity.User;
import com.eventbooking.entity.Venue;
import com.eventbooking.exception.BadRequestException;
import com.eventbooking.exception.ResourceNotFoundException;
import com.eventbooking.repository.BookingRepository;
import com.eventbooking.repository.EventRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Set;

/**
 * Handles event CRUD only. Seat-related concerns live in SeatService
 * (physical venue layout) and EventSeatService (per-event pricing +
 * availability) - kept separate so an event's seat map can be generated
 * as its own explicit admin step after the event itself is created,
 * rather than being implicitly baked into event creation.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class EventService {

    private final EventRepository eventRepository;
    private final VenueService venueService;
    private final BookingRepository bookingRepository;
    private final EmailService emailService;

    private static final DateTimeFormatter EMAIL_DATE_FORMAT = DateTimeFormatter.ofPattern("EEE, dd MMM yyyy \u00b7 hh:mm a");

    @Value("${app.uploads.poster-dir}")
    private String posterDir;

    private static final Set<String> ALLOWED_IMAGE_TYPES = Set.of("image/jpeg", "image/png", "image/webp");
    private static final long MAX_POSTER_SIZE_BYTES = 5L * 1024 * 1024; // 5MB

    public EventResponse createEvent(EventRequest request) {
        Venue venue = venueService.findVenueOrThrow(request.getVenueId());
        User currentUser = getCurrentUser();

        Event event = Event.builder()
                .title(request.getTitle())
                .description(request.getDescription())
                .category(request.getCategory())
                .eventDate(request.getEventDate())
                .venue(venue)
                .status(EventStatus.UPCOMING)
                .bookingEnabled(true)
                .createdBy(currentUser)
                .build();

        return toResponse(eventRepository.save(event));
    }

    public List<EventResponse> getAllUpcomingEvents() {
        return eventRepository.findByStatus(EventStatus.UPCOMING).stream()
                .map(this::toResponse)
                .toList();
    }

    // Admin-only "All Events" / history view - unlike getAllUpcomingEvents(),
    // this deliberately does not filter by status, so events that have moved
    // to COMPLETED (or CANCELLED) after their date has passed remain visible
    // instead of vanishing from the admin panel entirely.
    public List<EventResponse> getAllEventsForAdmin() {
        return eventRepository.findAllByOrderByEventDateDesc().stream()
                .map(this::toResponse)
                .toList();
    }

    public List<EventResponse> getEventsByCategory(String category) {
        return eventRepository.findByCategoryIgnoreCase(category).stream()
                .map(this::toResponse)
                .toList();
    }

    public EventResponse getEventById(Long id) {
        return toResponse(findEventOrThrow(id));
    }

    public Event findEventOrThrow(Long id) {
        return eventRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Event not found with id: " + id));
    }

    private User getCurrentUser() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        return (User) authentication.getPrincipal();
    }

    /**
     * Poster upload is deliberately its own endpoint, separate from
     * createEvent()/EventRequest - keeps the JSON event-creation flow simple
     * and lets the frontend upload (or skip) a poster as its own optional
     * step, since a poster is never required.
     *
     * Stored to local disk under app.uploads.poster-dir (same "fine for
     * this project, swap for S3 in production" simplification already used
     * for ticket PDFs - see TicketService). Overwrites any previous poster
     * for this event, so re-uploading just replaces it rather than
     * accumulating orphaned files.
     */
    public EventResponse uploadPosterImage(Long eventId, MultipartFile file) throws IOException {
        Event event = findEventOrThrow(eventId);

        if (file == null || file.isEmpty()) {
            throw new BadRequestException("No file was uploaded");
        }
        if (!ALLOWED_IMAGE_TYPES.contains(file.getContentType())) {
            throw new BadRequestException("Poster must be a JPEG, PNG, or WebP image");
        }
        if (file.getSize() > MAX_POSTER_SIZE_BYTES) {
            throw new BadRequestException("Poster image must be under 5MB");
        }

        String extension = switch (file.getContentType()) {
            case "image/png" -> ".png";
            case "image/webp" -> ".webp";
            default -> ".jpg";
        };

        Path dir = Path.of(posterDir);
        Files.createDirectories(dir);
        Path destination = dir.resolve("event-" + eventId + extension);
        Files.copy(file.getInputStream(), destination, StandardCopyOption.REPLACE_EXISTING);

        event.setPosterImageUrl("/uploads/posters/event-" + eventId + extension);
        return toResponse(eventRepository.save(event));
    }

    /**
     * Pauses/resumes bookability WITHOUT touching `status` - see the field
     * comment on Event.bookingEnabled for why these are kept independent.
     * Enforced in two places: SeatLockController (so a paused event can't
     * even have a seat selected) and BookingService.createBooking() as
     * defense-in-depth against a booking request that slipped in between a
     * seat being locked and the pause taking effect.
     */
    public EventResponse pauseBooking(Long eventId) {
        Event event = findEventOrThrow(eventId);
        event.setBookingEnabled(false);
        return toResponse(eventRepository.save(event));
    }

    public EventResponse resumeBooking(Long eventId) {
        Event event = findEventOrThrow(eventId);
        if (event.getStatus() == EventStatus.CANCELLED) {
            throw new BadRequestException("This event is cancelled - it can't be resumed for booking");
        }
        event.setBookingEnabled(true);
        return toResponse(eventRepository.save(event));
    }

    /**
     * Cancels the event and emails everyone with a CONFIRMED booking.
     * Deliberately does NOT touch those bookings' own status or trigger any
     * refund - refunds are still a manual process in this project (see
     * RefundPolicy.jsx), and a booking's CONFIRMED status doubles as the
     * payment/seat record support needs to look up when processing that
     * refund by hand. Cancelling the event is the trigger that starts that
     * human process, not something this method finishes end-to-end.
     */
    public EventResponse cancelEvent(Long eventId, String reason) {
        Event event = findEventOrThrow(eventId);
        event.setStatus(EventStatus.CANCELLED);
        event.setBookingEnabled(false);
        event.setCancellationReason(reason);
        Event saved = eventRepository.save(event);

        List<Booking> confirmedBookings = bookingRepository.findByEventIdAndStatus(eventId, BookingStatus.CONFIRMED);
        log.info("Event id={} cancelled - notifying {} confirmed booking(s)", eventId, confirmedBookings.size());
        for (Booking booking : confirmedBookings) {
            emailService.sendEventCancelledEmail(
                    booking.getUser().getEmail(),
                    booking.getUser().getName(),
                    event.getTitle(),
                    booking.getId(),
                    reason);
        }

        return toResponse(saved);
    }

    public EventResponse postponeEvent(Long eventId, LocalDateTime newEventDate, String note) {
        Event event = findEventOrThrow(eventId);
        String oldDateFormatted = event.getEventDate().format(EMAIL_DATE_FORMAT);
        String newDateFormatted = newEventDate.format(EMAIL_DATE_FORMAT);

        event.setEventDate(newEventDate);
        Event saved = eventRepository.save(event);

        List<Booking> confirmedBookings = bookingRepository.findByEventIdAndStatus(eventId, BookingStatus.CONFIRMED);
        log.info("Event id={} postponed - notifying {} confirmed booking(s)", eventId, confirmedBookings.size());
        for (Booking booking : confirmedBookings) {
            emailService.sendEventPostponedEmail(
                    booking.getUser().getEmail(),
                    booking.getUser().getName(),
                    event.getTitle(),
                    booking.getId(),
                    oldDateFormatted,
                    newDateFormatted,
                    note);
        }

        return toResponse(saved);
    }

    private EventResponse toResponse(Event event) {
        Venue venue = event.getVenue();
        VenueResponse venueResponse = VenueResponse.builder()
                .id(venue.getId())
                .name(venue.getName())
                .address(venue.getAddress())
                .city(venue.getCity())
                .totalCapacity(venue.getTotalCapacity())
                .build();

        return EventResponse.builder()
                .id(event.getId())
                .title(event.getTitle())
                .description(event.getDescription())
                .category(event.getCategory())
                .eventDate(event.getEventDate())
                .status(event.getStatus())
                .bookingEnabled(event.getBookingEnabled())
                .cancellationReason(event.getCancellationReason())
                .posterImageUrl(event.getPosterImageUrl())
                .venue(venueResponse)
                .build();
    }
}
