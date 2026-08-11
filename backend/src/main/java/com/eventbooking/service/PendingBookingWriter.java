package com.eventbooking.service;

import com.eventbooking.dto.payment.CreateBookingResponse;
import com.eventbooking.entity.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.eventbooking.repository.BookingRepository;
import java.math.BigDecimal;
import java.util.List;

/**
 * WHY THIS CLASS EXISTS: BookingService.createBooking() calls
 * razorpayService.createOrder() (an external network call) BEFORE any DB
 * write, specifically so a DB transaction is never held open for the
 * duration of that call. The actual DB persistence has to happen somewhere -
 * it can't just be a @Transactional method on BookingService itself, because
 * calling a @Transactional method from another method in the *same* class is
 * a plain Java method call, not a call through Spring's AOP proxy, so the
 * @Transactional annotation would silently do nothing (a well-known Spring
 * self-invocation gotcha). Putting it on a separate bean makes the call a
 * genuine cross-bean call that Spring actually intercepts and wraps in a
 * transaction.
 */
@Service
@RequiredArgsConstructor
public class PendingBookingWriter {

    private final BookingRepository bookingRepository;
    private final PaymentService paymentService;

    @Transactional
    public CreateBookingResponse persistPendingBooking(Event event, User user, List<EventSeat> seats,
                                                        BigDecimal total, String razorpayOrderId) {
        Booking booking = Booking.builder()
                .user(user)
                .event(event)
                .totalAmount(total)
                .status(BookingStatus.PENDING) // becomes CONFIRMED only after payment verification
                .build();
        booking = bookingRepository.save(booking);

        for (EventSeat seat : seats) {
            booking.getBookingSeats().add(BookingSeat.builder().booking(booking).eventSeat(seat).build());
        }
        bookingRepository.save(booking);

        return paymentService.recordPendingPayment(booking, razorpayOrderId);
    }
}
