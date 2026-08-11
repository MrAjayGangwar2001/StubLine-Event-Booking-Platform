package com.eventbooking.service;

import com.eventbooking.dto.booking.BookingResponse;
import com.eventbooking.dto.kafka.BookingConfirmedEvent;
import com.eventbooking.entity.*;
import com.eventbooking.exception.BadRequestException;
import com.eventbooking.exception.ResourceNotFoundException;
import com.eventbooking.exception.SeatUnavailableException;
import com.eventbooking.repository.BookingRepository;
import com.eventbooking.repository.EventSeatRepository;
import com.eventbooking.repository.PaymentRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * WHY THIS CLASS EXISTS: this is the "actually flip seats to BOOKED" logic
 * that used to live directly on BookingService.confirmBookingAfterPayment().
 * It got pulled out into its own bean so it can be called from TWO places -
 * PaymentController's post-payment flow, AND BookingService.createBooking()'s
 * free-booking path (total=0, nothing to actually pay, so it confirms
 * immediately instead of going through Razorpay) - without BookingService
 * calling one of its own @Transactional methods internally.
 *
 * That internal-call route was tried first via a self-injected, @Lazy
 * BookingService field, expecting Spring to hand back a proxied reference.
 * It didn't: Lombok's @RequiredArgsConstructor doesn't reliably carry the
 * @Lazy annotation onto the generated constructor parameter, so Spring saw
 * a plain non-lazy self-dependency and failed to start with "Requested bean
 * is currently in creation: Is there an unresolvable circular reference?".
 * A separate bean sidesteps the whole problem - same fix shape as
 * PendingBookingWriter, which exists for an analogous self-invocation
 * reason (see that class's comment).
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class BookingConfirmationService {

    private final BookingRepository bookingRepository;
    private final EventSeatRepository eventSeatRepository;
    private final PaymentRepository paymentRepository;
    private final SeatLockService seatLockService;
    private final BookingEventProducer bookingEventProducer;

    @Transactional
    public BookingResponse confirmBooking(Long bookingId, User currentUser) {
        Booking booking = bookingRepository.findById(bookingId)
                .orElseThrow(() -> new ResourceNotFoundException("Booking not found with id: " + bookingId));

        if (!booking.getUser().getId().equals(currentUser.getId())) {
            throw new BadRequestException("You can only confirm your own bookings");
        }
        if (booking.getStatus() != BookingStatus.PENDING) {
            // Already confirmed (e.g. duplicate callback) or expired/cancelled - don't re-process.
            throw new BadRequestException("This booking is not awaiting confirmation (status: " + booking.getStatus() + ")");
        }

        List<EventSeat> seats = booking.getBookingSeats().stream().map(BookingSeat::getEventSeat).toList();

        // Re-validate - payment (or, for a free booking, the extend-lock call
        // right before this) can take a moment, and re-checking here costs
        // nothing and catches any edge case where the hold didn't survive.
        for (EventSeat seat : seats) {
            if (seat.getStatus() != SeatStatus.AVAILABLE) {
                throw new SeatUnavailableException("One of your seats is no longer available. Contact support - your payment succeeded.");
            }
            if (!seatLockService.isLockedByUser(seat.getId(), currentUser.getId())) {
                throw new SeatUnavailableException("Your seat hold expired before payment could be confirmed. Contact support - your payment succeeded.");
            }
        }

        List<String> seatLabels = seats.stream()
                .map(s -> s.getSeat().getRowLabel() + s.getSeat().getSeatNumber())
                .toList();

        for (EventSeat seat : seats) {
            seat.setStatus(SeatStatus.BOOKED);
        }

        try {
            eventSeatRepository.saveAll(seats);
            eventSeatRepository.flush(); // forces the @Version check now, inside this try/catch
        } catch (OptimisticLockingFailureException ex) {
            throw new SeatUnavailableException("One of your selected seats was just booked by someone else. Contact support - your payment succeeded.");
        }

        booking.setStatus(BookingStatus.CONFIRMED);
        Booking saved = bookingRepository.save(booking);

        // Cleanup + notification from here on is best-effort - the booking is
        // already durably CONFIRMED in MySQL, so none of this should be able
        // to roll it back. See Week 3 fix notes for why this is try/caught.
        Long eventId = booking.getEvent().getId();
        for (EventSeat seat : seats) {
            try {
                seatLockService.clearLockAfterBooking(eventId, seat.getId(), currentUser.getId());
            } catch (Exception ex) {
                log.warn("Post-booking Redis cleanup failed for eventSeatId={}: {}", seat.getId(), ex.getMessage());
            }
        }

        try {
            bookingEventProducer.publishBookingConfirmed(BookingConfirmedEvent.builder()
                    .bookingId(saved.getId())
                    .userEmail(currentUser.getEmail())
                    .userName(currentUser.getName())
                    .eventTitle(booking.getEvent().getTitle())
                    .eventDate(booking.getEvent().getEventDate())
                    .seatLabels(seatLabels)
                    .totalAmount(saved.getTotalAmount())
                    .build());
        } catch (Exception ex) {
            log.warn("Failed to publish BookingConfirmedEvent for bookingId={}: {}", saved.getId(), ex.getMessage());
        }

        Payment payment = paymentRepository.findByBookingId(saved.getId()).orElse(null);

        return BookingResponse.builder()
                .id(saved.getId())
                .eventId(saved.getEvent().getId())
                .eventTitle(saved.getEvent().getTitle())
                .eventDate(saved.getEvent().getEventDate())
                .totalAmount(saved.getTotalAmount())
                .status(saved.getStatus())
                .seatLabels(seatLabels)
                .createdAt(saved.getCreatedAt())
                .paymentStatus(payment == null ? null : payment.getStatus().name())
                .razorpayPaymentId(payment == null ? null : payment.getProviderPaymentId())
                .build();
    }
}
