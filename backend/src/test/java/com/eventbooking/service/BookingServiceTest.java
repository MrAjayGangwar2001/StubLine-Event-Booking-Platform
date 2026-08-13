package com.eventbooking.service;

import com.eventbooking.dto.booking.CreateBookingRequest;
import com.eventbooking.dto.payment.CreateBookingResponse;
import com.eventbooking.entity.*;
import com.eventbooking.exception.BadRequestException;
import com.eventbooking.exception.SeatUnavailableException;
import com.eventbooking.repository.BookingRepository;
import com.eventbooking.repository.EventSeatRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Week 4: booking creation no longer confirms anything by itself - it now
 * only gets as far as creating a PENDING booking + Razorpay order. These
 * tests cover that boundary (createBooking) and the actual seat-booking step
 * (confirmBookingAfterPayment), which only PaymentService's verified callback
 * is meant to trigger.
 *
 * Note: the DB-persistence part of createBooking lives in PendingBookingWriter
 * (a separate bean, deliberately - see its class comment), so it's mocked
 * here rather than exercised directly; PendingBookingWriterTest below covers
 * its actual behavior.
 */
@ExtendWith(MockitoExtension.class)
class BookingServiceTest {

    @Mock private BookingRepository bookingRepository;
    @Mock private EventSeatRepository eventSeatRepository;
    @Mock private EventService eventService;
    @Mock private SeatLockService seatLockService;
    @Mock private PendingBookingWriter pendingBookingWriter;
    @Mock private RazorpayService razorpayService;
    @Mock private BookingEventProducer bookingEventProducer;

    @InjectMocks
    private BookingService bookingService;

    private User user;
    private Event event;
    private EventSeat availableSeat;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(bookingService, "paymentWindowMinutes", 10L);

        user = User.builder().id(1L).name("Ajay").email("ajay@example.com").role(Role.USER).build();
        Venue venue = Venue.builder().id(1L).name("Test Arena").city("Delhi").totalCapacity(100).build();
        event = Event.builder().id(10L).title("Concert Night").venue(venue).build();

        Seat physicalSeat = Seat.builder().id(100L).rowLabel("A").seatNumber(5).tier(SeatTier.GOLD).build();
        availableSeat = EventSeat.builder()
                .id(500L)
                .event(event)
                .seat(physicalSeat)
                .price(new BigDecimal("1500"))
                .status(SeatStatus.AVAILABLE)
                .build();

        Authentication auth = mock(Authentication.class);
        when(auth.getPrincipal()).thenReturn(user);
        SecurityContext securityContext = mock(SecurityContext.class);
        when(securityContext.getAuthentication()).thenReturn(auth);
        SecurityContextHolder.setContext(securityContext);
    }

    @Test
    void createBooking_createsOrderThenPersistsBooking_whenSeatLockedByCaller() {
        CreateBookingRequest request = new CreateBookingRequest();
        request.setEventId(10L);
        request.setEventSeatIds(List.of(500L));

        when(eventService.findEventOrThrow(10L)).thenReturn(event);
        when(eventSeatRepository.findAllById(List.of(500L))).thenReturn(List.of(availableSeat));
        when(seatLockService.isLockedByUser(500L, 1L)).thenReturn(true);
        when(razorpayService.createOrder(eq(new BigDecimal("1500")), anyString())).thenReturn("order_1");

        CreateBookingResponse expectedResponse = CreateBookingResponse.builder()
                .bookingId(99L).razorpayOrderId("order_1").razorpayKeyId("key_1")
                .amount(new BigDecimal("1500")).currency("INR").build();
        when(pendingBookingWriter.persistPendingBooking(eq(event), eq(user), anyList(), eq(new BigDecimal("1500")), eq("order_1")))
                .thenReturn(expectedResponse);

        CreateBookingResponse response = bookingService.createBooking(request);

        assertThat(response).isEqualTo(expectedResponse);
        // Seats must NOT be marked BOOKED at this stage - only after payment confirms.
        assertThat(availableSeat.getStatus()).isEqualTo(SeatStatus.AVAILABLE);
        // The hold should be extended to cover the payment window, not left at the short browsing TTL.
        verify(seatLockService).extendLock(eq(500L), eq(1L), any());
    }

    @Test
    void createBooking_neverCreatesOrderOrPersists_whenLockNotHeldByCaller() {
        CreateBookingRequest request = new CreateBookingRequest();
        request.setEventId(10L);
        request.setEventSeatIds(List.of(500L));

        when(eventService.findEventOrThrow(10L)).thenReturn(event);
        when(eventSeatRepository.findAllById(List.of(500L))).thenReturn(List.of(availableSeat));
        when(seatLockService.isLockedByUser(500L, 1L)).thenReturn(false);

        assertThatThrownBy(() -> bookingService.createBooking(request))
                .isInstanceOf(SeatUnavailableException.class);

        // Validation must fail BEFORE the external Razorpay call, not after -
        // no reason to create an order for a seat we already know isn't held.
        verify(razorpayService, never()).createOrder(any(), any());
        verify(pendingBookingWriter, never()).persistPendingBooking(any(), any(), any(), any(), any());
    }

    @Test
    void confirmBookingAfterPayment_marksSeatsBooked_andPublishesKafkaEvent() {
        Booking booking = Booking.builder()
                .id(99L).user(user).event(event).totalAmount(new BigDecimal("1500"))
                .status(BookingStatus.PENDING)
                .build();
        booking.getBookingSeats().add(BookingSeat.builder().booking(booking).eventSeat(availableSeat).build());

        when(bookingRepository.findById(99L)).thenReturn(Optional.of(booking));
        when(seatLockService.isLockedByUser(500L, 1L)).thenReturn(true);
        when(eventSeatRepository.saveAll(anyList())).thenAnswer(inv -> inv.getArgument(0));
        when(bookingRepository.save(any(Booking.class))).thenAnswer(inv -> inv.getArgument(0));

        var response = bookingService.confirmBookingAfterPayment(99L);

        assertThat(response.getStatus()).isEqualTo(BookingStatus.CONFIRMED);
        assertThat(availableSeat.getStatus()).isEqualTo(SeatStatus.BOOKED);
        verify(seatLockService).clearLockAfterBooking(10L, 500L, 1L);
        verify(bookingEventProducer).publishBookingConfirmed(argThat(e ->
                e.getBookingId().equals(99L) && e.getSeatLabels().equals(List.of("A5"))));
    }

    @Test
    void confirmBookingAfterPayment_rejects_whenBookingNotPending() {
        Booking alreadyConfirmed = Booking.builder()
                .id(99L).user(user).event(event).totalAmount(new BigDecimal("1500"))
                .status(BookingStatus.CONFIRMED)
                .build();

        when(bookingRepository.findById(99L)).thenReturn(Optional.of(alreadyConfirmed));

        assertThatThrownBy(() -> bookingService.confirmBookingAfterPayment(99L))
                .isInstanceOf(BadRequestException.class);

        verify(bookingEventProducer, never()).publishBookingConfirmed(any());
    }

    @Test
    void confirmBookingAfterPayment_rejects_whenCallerDoesNotOwnBooking() {
        User someoneElse = User.builder().id(2L).name("Other").email("other@example.com").role(Role.USER).build();
        Booking booking = Booking.builder()
                .id(99L).user(someoneElse).event(event).totalAmount(new BigDecimal("1500"))
                .status(BookingStatus.PENDING)
                .build();

        when(bookingRepository.findById(99L)).thenReturn(Optional.of(booking));

        assertThatThrownBy(() -> bookingService.confirmBookingAfterPayment(99L))
                .isInstanceOf(BadRequestException.class);
    }
}
