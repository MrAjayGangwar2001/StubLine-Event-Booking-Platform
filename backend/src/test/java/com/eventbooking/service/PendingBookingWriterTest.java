package com.eventbooking.service;

import com.eventbooking.dto.payment.CreateBookingResponse;
import com.eventbooking.entity.*;
import com.eventbooking.repository.BookingRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PendingBookingWriterTest {

    @Mock private BookingRepository bookingRepository;
    @Mock private PaymentService paymentService;

    @InjectMocks
    private PendingBookingWriter writer;

    private User user;
    private Event event;
    private EventSeat seat;

    @BeforeEach
    void setUp() {
        user = User.builder().id(1L).name("Ajay").email("ajay@example.com").role(Role.USER).build();
        Venue venue = Venue.builder().id(1L).name("Test Arena").city("Delhi").build();
        event = Event.builder().id(10L).title("Concert Night").venue(venue).build();
        Seat physicalSeat = Seat.builder().id(100L).rowLabel("A").seatNumber(5).tier(SeatTier.GOLD).build();
        seat = EventSeat.builder().id(500L).event(event).seat(physicalSeat)
                .price(new BigDecimal("1500")).status(SeatStatus.AVAILABLE).build();
    }

    @Test
    void persistPendingBooking_createsBookingAndBookingSeats_thenDelegatesToPaymentService() {
        when(bookingRepository.save(any(Booking.class))).thenAnswer(inv -> inv.getArgument(0));
        CreateBookingResponse expected = CreateBookingResponse.builder()
                .bookingId(1L).razorpayOrderId("order_1").build();
        when(paymentService.recordPendingPayment(any(Booking.class), eq("order_1")))
                .thenReturn(expected);

        CreateBookingResponse result = writer.persistPendingBooking(
                event, user, List.of(seat), new BigDecimal("1500"), "order_1");

        assertThat(result).isEqualTo(expected);
        verify(paymentService).recordPendingPayment(
                argThat(b -> b.getStatus() == BookingStatus.PENDING
                        && b.getTotalAmount().compareTo(new BigDecimal("1500")) == 0
                        && b.getBookingSeats().size() == 1),
                eq("order_1"));
    }
}
