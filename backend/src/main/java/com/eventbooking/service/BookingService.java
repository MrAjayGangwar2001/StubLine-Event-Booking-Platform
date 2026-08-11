package com.eventbooking.service;

import com.eventbooking.dto.booking.BookingResponse;
import com.eventbooking.dto.booking.CreateBookingRequest;
import com.eventbooking.dto.kafka.BookingConfirmedEvent;
import com.eventbooking.dto.payment.CreateBookingResponse;
import com.eventbooking.entity.*;
import com.eventbooking.exception.BadRequestException;
import com.eventbooking.exception.ResourceNotFoundException;
import com.eventbooking.exception.SeatUnavailableException;
import com.eventbooking.repository.BookingRepository;
import com.eventbooking.repository.EventSeatRepository;
import com.eventbooking.repository.PaymentRepository;
import com.google.zxing.WriterException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.UUID;

/**
 * WEEK 4: booking is now a two-phase flow instead of instant confirmation.
 *
 *   1. createBooking()  - validates the seat lock, creates a PENDING booking
 *      + a Razorpay order, extends the Redis hold to cover the payment
 *      window. Seats stay AVAILABLE in MySQL the whole time; the Redis lock
 *      (now overlaid onto the seat-map GET response too, see
 *      EventSeatService) is what actually keeps other users out.
 *   2. confirmBookingAfterPayment() - called only after PaymentService has
 *      verified the payment signature. This is where seats actually flip to
 *      BOOKED, the same Week 2/3 concurrency defenses apply (Redis lock
 *      ownership + @Version as backup), and the Kafka confirmation event
 *      fires.
 *   3. cancelPendingBooking() - user backs out of checkout before paying;
 *      releases the Redis hold immediately instead of making everyone else
 *      wait out the payment-window TTL for no reason.
 *
 * If a user just closes the tab mid-payment without triggering step 3,
 * PendingBookingCleanupJob (a scheduled task) eventually marks the booking
 * EXPIRED - see that class for details.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class BookingService {

    private final BookingRepository bookingRepository;
    private final EventSeatRepository eventSeatRepository;
    private final EventService eventService;
    private final SeatLockService seatLockService;
    private final PendingBookingWriter pendingBookingWriter;
    private final RazorpayService razorpayService;
    private final PaymentService paymentService;
    private final PaymentRepository paymentRepository;
    private final TicketService ticketService;
    private final BookingConfirmationService bookingConfirmationService;

    @Value("${app.booking.payment-window-minutes}")
    private long paymentWindowMinutes;

    /**
     * Deliberately NOT @Transactional at the top level: razorpayService.createOrder()
     * is a network call to a third party, and holding a DB connection open for
     * however long that round trip takes (could be slow, could time out, could
     * hang on a bad network) starves the connection pool under any real load.
     * Only the actual DB work (PendingBookingWriter, a separate bean - see its
     * class comment for why it has to be separate) runs inside a transaction,
     * and only after the external call has already succeeded.
     */
    public CreateBookingResponse createBooking(CreateBookingRequest request) {
        Event event = eventService.findEventOrThrow(request.getEventId());
        User currentUser = getCurrentUser();

        // Defense-in-depth: SeatLockController already checks this before a
        // seat can even be locked, but an admin could pause/cancel the
        // event in the window between "seat locked" and "checkout
        // submitted" - re-check here rather than trust that the earlier
        // check still holds.
        if (!event.isBookable()) {
            throw new BadRequestException(event.getStatus() == EventStatus.CANCELLED
                    ? "This event has been cancelled and is no longer bookable"
                    : "Booking is currently paused for this event. Please check back later.");
        }

        List<EventSeat> seats = eventSeatRepository.findAllByIdWithSeatAndEvent(request.getEventSeatIds());
        validateSeatsSelectable(seats, request, event, currentUser);

        BigDecimal total = seats.stream()
                .map(EventSeat::getPrice)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        // FREE booking (every selected seat priced 0 - e.g. a free event, or
        // a free-tier seat with no other seat in the same request). Razorpay's
        // Orders API hard-rejects amount=0 with a 400 - there is no such
        // thing as a zero-rupee "order" to them - which is exactly what
        // produced "Could not initiate payment" for every free booking.
        // There's nothing to actually charge, so skip Razorpay entirely
        // and confirm the booking right away instead of routing it through
        // a gateway call that was never going to succeed.
        if (total.compareTo(BigDecimal.ZERO) == 0) {
            CreateBookingResponse response =
                    pendingBookingWriter.persistPendingBooking(event, currentUser, seats, total, null);

            Duration paymentWindow = Duration.ofMinutes(paymentWindowMinutes);
            for (EventSeat seat : seats) {
                seatLockService.extendLock(seat.getId(), currentUser.getId(), paymentWindow);
            }

            paymentService.markFreeBookingPaymentSuccess(response.getBookingId());
            bookingConfirmationService.confirmBooking(response.getBookingId(), currentUser);

            response.setPaymentRequired(false);
            response.setRazorpayOrderId(null);
            response.setRazorpayKeyId(null);
            return response;
        }

        // Order creation is an external call that can fail (bad keys, Razorpay
        // outage, network blip) - do it BEFORE touching the DB at all, so a
        // failure here leaves nothing half-written to clean up.
        // Razorpay's `receipt` field has a hard 40-character limit - a plain
        // UUID is 36 chars, which fits; "booking-" + UUID (44 chars) doesn't,
        // and silently fails every single order creation with a 400 Bad Request
        // ("receipt: the length must be no more than 40"). No booking id exists
        // yet at this point (order creation deliberately happens before any DB
        // write - see the class comment above), so a UUID is what we have to
        // work with as a unique-enough identifier here.
        String receipt = UUID.randomUUID().toString();
        String razorpayOrderId = razorpayService.createOrder(total, receipt);

        CreateBookingResponse response =
                pendingBookingWriter.persistPendingBooking(event, currentUser, seats, total, razorpayOrderId);

        Duration paymentWindow = Duration.ofMinutes(paymentWindowMinutes);
        for (EventSeat seat : seats) {
            seatLockService.extendLock(seat.getId(), currentUser.getId(), paymentWindow);
        }

        return response;
    }

    private void validateSeatsSelectable(List<EventSeat> seats, CreateBookingRequest request, Event event, User currentUser) {
        if (seats.size() != request.getEventSeatIds().size()) {
            throw new ResourceNotFoundException("One or more selected seats do not exist");
        }
        for (EventSeat seat : seats) {
            if (!seat.getEvent().getId().equals(event.getId())) {
                throw new BadRequestException("Seat " + seat.getId() + " does not belong to this event");
            }
            if (seat.getStatus() != SeatStatus.AVAILABLE) {
                throw new SeatUnavailableException(
                        "Seat " + seat.getSeat().getRowLabel() + seat.getSeat().getSeatNumber() + " is no longer available");
            }
            if (!seatLockService.isLockedByUser(seat.getId(), currentUser.getId())) {
                throw new SeatUnavailableException(
                        "Your hold on seat " + seat.getSeat().getRowLabel() + seat.getSeat().getSeatNumber()
                                + " has expired. Please select it again.");
            }
        }
    }

    /**
     * Called by PaymentController once PaymentService confirms the Razorpay
     * signature is genuine. This is the ONLY place seats actually flip to
     * BOOKED - everything before this point (locking, order creation) was
     * just reserving the option to book, not booking itself.
     *
     * Delegates to BookingConfirmationService - see that class's comment for
     * why this logic isn't inline here anymore (needed to also be callable
     * from createBooking()'s free-booking path without a self-invocation /
     * circular-bean problem).
     */
    public BookingResponse confirmBookingAfterPayment(Long bookingId) {
        return bookingConfirmationService.confirmBooking(bookingId, getCurrentUser());
    }

    /**
     * User backs out of the Razorpay checkout modal before paying. Frees the
     * seats immediately instead of leaving them held for the full payment
     * window for nothing.
     */
    @Transactional
    public void cancelPendingBooking(Long bookingId) {
        User currentUser = getCurrentUser();
        Booking booking = bookingRepository.findById(bookingId)
                .orElseThrow(() -> new ResourceNotFoundException("Booking not found with id: " + bookingId));

        if (!booking.getUser().getId().equals(currentUser.getId())) {
            throw new BadRequestException("You can only cancel your own bookings");
        }
        if (booking.getStatus() != BookingStatus.PENDING) {
            throw new BadRequestException("Only a pending booking can be cancelled this way");
        }

        booking.setStatus(BookingStatus.CANCELLED);
        bookingRepository.save(booking);

        Long eventId = booking.getEvent().getId();
        for (BookingSeat bs : booking.getBookingSeats()) {
            try {
                seatLockService.release(eventId, bs.getEventSeat().getId(), currentUser.getId());
            } catch (Exception ex) {
                log.warn("Failed to release lock for eventSeatId={} on pending-booking cancel: {}",
                        bs.getEventSeat().getId(), ex.getMessage());
            }
        }
    }

    /**
     * Cancels an already-CONFIRMED booking (post-payment). No refund flow is
     * wired up here - that's a real payment-gateway integration (Razorpay
     * Refunds API) beyond this project's current scope, noted rather than
     * silently skipped.
     */
    @Transactional
    public BookingResponse cancelBooking(Long bookingId) {
        User currentUser = getCurrentUser();
        Booking booking = bookingRepository.findById(bookingId)
                .orElseThrow(() -> new ResourceNotFoundException("Booking not found with id: " + bookingId));

        if (!booking.getUser().getId().equals(currentUser.getId())) {
            throw new BadRequestException("You can only cancel your own bookings");
        }
        if (booking.getStatus() != BookingStatus.CONFIRMED) {
            throw new BadRequestException("Only confirmed bookings can be cancelled");
        }

        booking.setStatus(BookingStatus.CANCELLED);

        List<String> seatLabels = booking.getBookingSeats().stream()
                .map(bs -> {
                    EventSeat es = bs.getEventSeat();
                    es.setStatus(SeatStatus.AVAILABLE);
                    return es.getSeat().getRowLabel() + es.getSeat().getSeatNumber();
                })
                .toList();

        Booking saved = bookingRepository.save(booking);

        Long eventId = saved.getEvent().getId();
        for (BookingSeat bs : saved.getBookingSeats()) {
            try {
                seatLockService.broadcastAvailable(eventId, bs.getEventSeat().getId());
            } catch (Exception ex) {
                log.warn("Post-cancellation WebSocket broadcast failed for eventSeatId={}: {}",
                        bs.getEventSeat().getId(), ex.getMessage());
            }
        }

        return toResponse(saved, seatLabels);
    }

    public List<BookingResponse> getMyBookings() {
        User currentUser = getCurrentUser();
        return bookingRepository.findByUserIdWithDetails(currentUser.getId()).stream()
                .map(b -> toResponse(b, b.getBookingSeats().stream()
                        .map(bs -> bs.getEventSeat().getSeat().getRowLabel() + bs.getEventSeat().getSeat().getSeatNumber())
                        .toList()))
                .toList();
    }

    private User getCurrentUser() {
        return (User) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
    }

    private BookingResponse toResponse(Booking booking, List<String> seatLabels) {
        Payment payment = paymentRepository.findByBookingId(booking.getId()).orElse(null);

        return BookingResponse.builder()
                .id(booking.getId())
                .eventId(booking.getEvent().getId())
                .eventTitle(booking.getEvent().getTitle())
                .eventDate(booking.getEvent().getEventDate())
                .totalAmount(booking.getTotalAmount())
                .status(booking.getStatus())
                .seatLabels(seatLabels)
                .createdAt(booking.getCreatedAt())
                .paymentStatus(payment == null ? null : payment.getStatus().name())
                .razorpayPaymentId(payment == null ? null : payment.getProviderPaymentId())
                .build();
    }

    /**
     * Regenerates the ticket/invoice PDF on demand instead of relying on the
     * copy TicketService already saved to disk and emailed after payment.
     * Two reasons this matters, not just one:
     *
     *  - The on-disk file (app.tickets.output-dir) isn't guaranteed to still
     *    be there - nothing currently backs it up, and it was never meant to
     *    be a permanent store, just a staging spot for the email attachment.
     *  - More importantly: emailing it was previously the ONLY way to see
     *    it at all. A user who logged in with a password (no functioning
     *    inbox, a typo'd email, spam-filtered mail, whatever) had no way to
     *    view or download their invoice from inside the app itself, even
     *    though "My Bookings" already shows them everything needed to
     *    rebuild it. This endpoint is what fixes that gap.
     */
    public byte[] downloadInvoice(Long bookingId) throws IOException {
        Booking booking = bookingRepository.findById(bookingId)
                .orElseThrow(() -> new ResourceNotFoundException("Booking not found with id: " + bookingId));
        User currentUser = getCurrentUser();

        if (!booking.getUser().getId().equals(currentUser.getId())) {
            throw new BadRequestException("You can only view your own bookings");
        }
        if (booking.getStatus() == BookingStatus.PENDING || booking.getStatus() == BookingStatus.EXPIRED) {
            throw new BadRequestException("No invoice available - this booking was never completed");
        }

        List<String> seatLabels = booking.getBookingSeats().stream()
                .map(bs -> bs.getEventSeat().getSeat().getRowLabel() + bs.getEventSeat().getSeat().getSeatNumber())
                .toList();

        BookingConfirmedEvent event = BookingConfirmedEvent.builder()
                .bookingId(booking.getId())
                .userEmail(currentUser.getEmail())
                .userName(currentUser.getName())
                .eventTitle(booking.getEvent().getTitle())
                .eventDate(booking.getEvent().getEventDate())
                .seatLabels(seatLabels)
                .totalAmount(booking.getTotalAmount())
                .build();

        Path pdfPath;
        try {
            pdfPath = ticketService.generateTicketPdf(event);
        } catch (WriterException ex) {
            throw new IOException("Could not generate the invoice PDF", ex);
        }

        return Files.readAllBytes(pdfPath);
    }
}
