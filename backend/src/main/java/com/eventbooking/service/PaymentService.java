package com.eventbooking.service;

import com.eventbooking.dto.payment.CreateBookingResponse;
import com.eventbooking.dto.payment.VerifyPaymentRequest;
import com.eventbooking.entity.Booking;
import com.eventbooking.entity.Payment;
import com.eventbooking.entity.PaymentStatus;
import com.eventbooking.exception.BadRequestException;
import com.eventbooking.exception.ResourceNotFoundException;
import com.eventbooking.repository.PaymentRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class PaymentService {

    private final PaymentRepository paymentRepository;
    private final RazorpayService razorpayService;

    @Value("${app.razorpay.key-id}")
    private String keyId;

    /**
     * Persists a PENDING Payment row for an order that's already been created
     * with Razorpay. Deliberately does NOT call Razorpay itself - see
     * BookingService.createBooking() for why the external call happens
     * outside any DB transaction, before this method is invoked.
     */
    @Transactional
    public CreateBookingResponse recordPendingPayment(Booking booking, String razorpayOrderId) {
        Payment payment = Payment.builder()
                .booking(booking)
                .providerOrderId(razorpayOrderId)
                .amount(booking.getTotalAmount())
                .status(PaymentStatus.PENDING)
                // Client never sends this - generated server-side so retries of the
                // *same* checkout attempt are identifiable, without trusting a
                // client-supplied key that could collide or be reused maliciously.
                .idempotencyKey(UUID.randomUUID().toString())
                .build();
        paymentRepository.save(payment);

        return CreateBookingResponse.builder()
                .bookingId(booking.getId())
                .razorpayOrderId(razorpayOrderId)
                .razorpayKeyId(keyId)
                .amount(booking.getTotalAmount())
                .currency("INR")
                .build();
    }

    /**
     * For a free booking (total amount 0 - e.g. a free event, or a
     * free-tier seat), there's no real payment to make and no order was
     * ever created with Razorpay (see BookingService.createBooking() - a
     * zero-amount order is outright rejected by Razorpay's API, which used
     * to surface as "Could not initiate payment" for every free booking).
     * The Payment row still exists (recordPendingPayment always creates
     * one) so history/reporting has a consistent record either way; this
     * just marks it SUCCESS immediately instead of leaving it PENDING
     * forever with no gateway callback ever coming to resolve it.
     */
    @Transactional
    public void markFreeBookingPaymentSuccess(Long bookingId) {
        Payment payment = paymentRepository.findByBookingId(bookingId)
                .orElseThrow(() -> new ResourceNotFoundException("No payment found for booking " + bookingId));
        payment.setStatus(PaymentStatus.SUCCESS);
        paymentRepository.save(payment);
    }

    /**
     * Verifies a Razorpay checkout callback is genuine before treating the
     * payment as successful. Returns the booking id (not the Payment entity
     * itself) deliberately - Payment.booking is a lazy association, and by
     * the time this @Transactional method returns, the Hibernate session
     * backing that lazy proxy is closed. The caller (BookingController)
     * would hit a LazyInitializationException trying to read
     * payment.getBooking().getId() outside this transaction, so we resolve
     * that id in here instead, while the session is still open.
     */
    @Transactional
    public Long verifyPayment(VerifyPaymentRequest request) {
        Payment payment = paymentRepository.findByBookingId(request.getBookingId())
                .orElseThrow(() -> new ResourceNotFoundException("No payment found for booking " + request.getBookingId()));

        if (payment.getStatus() == PaymentStatus.SUCCESS) {
            // Already verified - e.g. the frontend retried this call after a
            // network blip on the first (successful) response. Returning the
            // existing booking id is safer than re-verifying and risking two
            // different outcomes for what should be a single payment event.
            return request.getBookingId();
        }

        if (!payment.getProviderOrderId().equals(request.getRazorpayOrderId())) {
            throw new BadRequestException("Order id does not match this booking's payment");
        }

        boolean valid = razorpayService.verifySignature(
                request.getRazorpayOrderId(), request.getRazorpayPaymentId(), request.getRazorpaySignature());

        if (!valid) {
            payment.setStatus(PaymentStatus.FAILED);
            paymentRepository.save(payment);
            log.warn("Payment signature verification FAILED for bookingId={}", request.getBookingId());
            throw new BadRequestException("Payment verification failed. If money was deducted, it will be auto-refunded by Razorpay.");
        }

        payment.setStatus(PaymentStatus.SUCCESS);
        payment.setProviderPaymentId(request.getRazorpayPaymentId());
        paymentRepository.save(payment);
        return request.getBookingId();
    }
}
