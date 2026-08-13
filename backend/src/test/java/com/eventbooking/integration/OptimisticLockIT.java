package com.eventbooking.integration;

import com.eventbooking.entity.*;
import com.eventbooking.repository.EventRepository;
import com.eventbooking.repository.EventSeatRepository;
import com.eventbooking.repository.SeatRepository;
import com.eventbooking.repository.UserRepository;
import com.eventbooking.repository.VenueRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Deliberately bypasses SeatLockService/Redis entirely - this test exists to
 * prove EventSeat's @Version column (the Week 2 defense) still works on its
 * own against a REAL database, which matters because it's the fallback that
 * catches anything Redis somehow misses (e.g. a lock that expired a moment
 * too early). Two real threads, each in their own transaction, both read the
 * same row before either writes - forcing the exact race the @Version column
 * exists to catch.
 */
class OptimisticLockIT extends AbstractIntegrationTest {

    @Autowired private EventSeatRepository eventSeatRepository;
    @Autowired private VenueRepository venueRepository;
    @Autowired private SeatRepository seatRepository;
    @Autowired private EventRepository eventRepository;
    @Autowired private UserRepository userRepository;
    @Autowired private PasswordEncoder passwordEncoder;
    @Autowired private PlatformTransactionManager transactionManager;

    private Long eventSeatId;

    @BeforeEach
    void setUp() {
        User admin = userRepository.save(User.builder()
                .name("Test Admin").email("optimistic-lock-test@example.com")
                .passwordHash(passwordEncoder.encode("password"))
                .role(Role.ADMIN)
                .build());

        Venue venue = venueRepository.save(Venue.builder()
                .name("Test Venue").address("123 Test St").city("Delhi")
                .totalCapacity(1)
                .build());

        Seat seat = seatRepository.save(Seat.builder()
                .venue(venue).rowLabel("A").seatNumber(1).tier(SeatTier.GOLD)
                .build());

        Event event = eventRepository.save(Event.builder()
                .venue(venue).title("Optimistic Lock Test Event").category("Test")
                .eventDate(LocalDateTime.now().plusDays(30))
                .status(EventStatus.UPCOMING)
                .createdBy(admin)
                .build());

        EventSeat eventSeat = eventSeatRepository.save(EventSeat.builder()
                .event(event).seat(seat)
                .price(new BigDecimal("1000"))
                .status(SeatStatus.AVAILABLE)
                .build());

        eventSeatId = eventSeat.getId();
    }

    @Test
    void twoThreads_bothReadThenBothWrite_onlyOneCommitsSuccessfully() throws Exception {
        TransactionTemplate transactionTemplate = new TransactionTemplate(transactionManager);

        CountDownLatch bothHaveRead = new CountDownLatch(2);
        CountDownLatch releaseGate = new CountDownLatch(1);
        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger conflictCount = new AtomicInteger(0);

        Runnable attemptBooking = () -> {
            try {
                transactionTemplate.executeWithoutResult(status -> {
                    EventSeat seat = eventSeatRepository.findById(eventSeatId).orElseThrow();

                    bothHaveRead.countDown();
                    try {
                        releaseGate.await(5, TimeUnit.SECONDS);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    }

                    // Both threads now hold a copy of the seat read at the SAME
                    // version - exactly the race @Version exists to catch.
                    seat.setStatus(SeatStatus.BOOKED);
                    eventSeatRepository.saveAndFlush(seat);
                });
                successCount.incrementAndGet();
            } catch (OptimisticLockingFailureException ex) {
                conflictCount.incrementAndGet();
            }
        };

        ExecutorService executor = Executors.newFixedThreadPool(2);
        Future<?> f1 = executor.submit(attemptBooking);
        Future<?> f2 = executor.submit(attemptBooking);

        bothHaveRead.await(5, TimeUnit.SECONDS);
        releaseGate.countDown();

        f1.get(10, TimeUnit.SECONDS);
        f2.get(10, TimeUnit.SECONDS);
        executor.shutdown();

        assertThat(successCount.get())
                .as("exactly one of the two concurrent writes should commit")
                .isEqualTo(1);
        assertThat(conflictCount.get())
                .as("the other should fail with an optimistic locking conflict, not silently overwrite")
                .isEqualTo(1);

        EventSeat finalState = eventSeatRepository.findById(eventSeatId).orElseThrow();
        assertThat(finalState.getStatus()).isEqualTo(SeatStatus.BOOKED);
        assertThat(finalState.getVersion()).isEqualTo(1L); // bumped exactly once, not twice
    }

    @Test
    void sequentialUpdates_bothSucceed_sinceThereIsNoActualConflict() {
        // Sanity check / contrast case: the @Version column should NOT block
        // legitimate sequential updates - only genuinely concurrent ones.
        EventSeat seat = eventSeatRepository.findById(eventSeatId).orElseThrow();
        seat.setStatus(SeatStatus.BOOKED);
        eventSeatRepository.saveAndFlush(seat);

        EventSeat rebooked = eventSeatRepository.findById(eventSeatId).orElseThrow();
        rebooked.setStatus(SeatStatus.AVAILABLE); // e.g. a cancellation
        eventSeatRepository.saveAndFlush(rebooked);

        EventSeat finalState = eventSeatRepository.findById(eventSeatId).orElseThrow();
        assertThat(finalState.getStatus()).isEqualTo(SeatStatus.AVAILABLE);
        assertThat(finalState.getVersion()).isEqualTo(2L);
    }
}
