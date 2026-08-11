package com.eventbooking.service;

import com.eventbooking.entity.Booking;
import com.eventbooking.entity.BookingSeat;
import com.eventbooking.entity.BookingStatus;
import com.eventbooking.repository.BookingRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Safety net for checkouts nobody ever finished or explicitly cancelled -
 * e.g. a user closes the browser tab mid-payment. The Redis lock on their
 * seats will have already expired on its own by then (its TTL matches the
 * payment window), so this job isn't what's actually freeing the seats -
 * that already happened. What this job cleans up is the MySQL side: without
 * it, an abandoned booking would sit at status=PENDING forever, cluttering
 * "My Bookings" and any future admin reporting with checkouts that never
 * completed.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class PendingBookingCleanupJob {

    private final BookingRepository bookingRepository;
    private final SeatLockService seatLockService;

    @Value("${app.booking.payment-window-minutes}")
    private long paymentWindowMinutes;

    @Scheduled(fixedRate = 60_000) // every minute - cheap query, cheap to run often
    @Transactional
    public void expireAbandonedPendingBookings() {
        // A little slack beyond the exact payment window, so this never races
        // against a payment that's genuinely still being verified right at
        // the boundary.
        LocalDateTime cutoff = LocalDateTime.now().minusMinutes(paymentWindowMinutes + 2);

        List<Booking> stale = bookingRepository.findByStatusAndCreatedAtBefore(BookingStatus.PENDING, cutoff);
        if (stale.isEmpty()) {
            return;
        }

        log.info("Expiring {} abandoned pending booking(s)", stale.size());

        for (Booking booking : stale) {
            booking.setStatus(BookingStatus.EXPIRED);
            for (BookingSeat bs : booking.getBookingSeats()) {
                try {
                    // Almost always a no-op by now (TTL already did the real
                    // work) - this just covers the rare case the lock is
                    // somehow still held, e.g. if the payment window config
                    // was reduced after the lock was originally extended.
                    seatLockService.release(booking.getEvent().getId(), bs.getEventSeat().getId(), booking.getUser().getId());
                } catch (Exception ex) {
                    log.warn("Failed to release lock while expiring bookingId={}: {}", booking.getId(), ex.getMessage());
                }
            }
        }

        bookingRepository.saveAll(stale);
    }
}
