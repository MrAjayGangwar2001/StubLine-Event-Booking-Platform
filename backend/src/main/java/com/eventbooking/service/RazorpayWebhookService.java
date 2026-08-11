package com.eventbooking.service;

import com.eventbooking.entity.Booking;
import com.eventbooking.entity.BookingStatus;
import com.eventbooking.entity.Payment;
import com.eventbooking.entity.PaymentStatus;
import com.eventbooking.exception.BadRequestException;
import com.eventbooking.repository.PaymentRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;

/**
 * Handles incoming Razorpay webhook calls - the backup path for confirming a
 * payment when the user's own browser never gets to call
 * POST /bookings/verify-payment (network drop, tab closed, app backgrounded
 * mid-payment, etc). Without this, a payment could succeed on Razorpay's
 * side while the booking sits PENDING and eventually expires -
 * PendingBookingCleanupJob only knows to release the seat lock, it has no
 * way to know Razorpay actually captured a payment for it.
 *
 * This is genuinely a SECOND, independent confirmation path, not a
 * replacement for the client-driven one - most bookings will still get
 * confirmed by the browser's own verify-payment call, arriving before this
 * webhook does. This exists purely to catch the cases where that doesn't
 * happen.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class RazorpayWebhookService {

    private final RazorpayService razorpayService;
    private final PaymentRepository paymentRepository;
    private final BookingConfirmationService bookingConfirmationService;
    private final ObjectMapper objectMapper;

    @Transactional
    public void handleEvent(String rawBody, String signatureHeader) {
        if (signatureHeader == null || !razorpayService.verifyWebhookSignature(rawBody, signatureHeader)) {
            // Never trust an unsigned or wrongly-signed call - this endpoint
            // is public (no JWT), so the signature is the ONLY thing
            // proving this request genuinely came from Razorpay and not
            // someone who found the URL and POSTed a fake "success" event.
            throw new BadRequestException("Invalid or missing webhook signature");
        }

        JsonNode json;
        try {
            json = objectMapper.readTree(rawBody);
        } catch (IOException ex) {
            throw new BadRequestException("Malformed webhook payload");
        }

        String eventType = json.path("event").asText();

        // Only payment.captured actually needs action here. Razorpay sends
        // many other event types (order.paid, payment.failed, refund.*,
        // etc.) to the same URL - silently ignoring anything we don't
        // specifically handle is correct webhook behavior, not a bug.
        if (!"payment.captured".equals(eventType)) {
            log.debug("Ignoring Razorpay webhook event type: {}", eventType);
            return;
        }

        JsonNode paymentEntity = json.path("payload").path("payment").path("entity");
        String orderId = paymentEntity.path("order_id").asText(null);
        String paymentId = paymentEntity.path("id").asText(null);

        if (orderId == null || paymentId == null) {
            throw new BadRequestException("payment.captured webhook missing order_id/payment_id");
        }

        Payment payment = paymentRepository.findByProviderOrderId(orderId).orElse(null);
        if (payment == null) {
            // Could be an order from a different environment (test vs live
            // keys pointed at the same webhook URL during a migration), or
            // simply a booking that was never persisted. Log and move on -
            // there's nothing in our DB to act on.
            log.warn("Webhook payment.captured for unknown Razorpay order_id={}", orderId);
            return;
        }

        if (payment.getStatus() == PaymentStatus.SUCCESS) {
            // Already confirmed - almost certainly via the client's own
            // verify-payment call beating this webhook here, which is the
            // common case. Nothing to do; returning quietly (not an error)
            // is what makes this safe to call more than once.
            log.debug("Webhook payment.captured for already-SUCCESS bookingId={} - no-op", payment.getBooking().getId());
            return;
        }

        payment.setStatus(PaymentStatus.SUCCESS);
        payment.setProviderPaymentId(paymentId);
        paymentRepository.save(payment);

        Booking booking = payment.getBooking();
        if (booking.getStatus() == BookingStatus.PENDING) {
            log.info("Webhook confirming bookingId={} - the client's own verify-payment call never arrived", booking.getId());
            try {
                // Passing booking.getUser() instead of the usual "current
                // logged-in user" - there IS no logged-in user on this
                // request, Razorpay's servers called this, not a browser
                // with a JWT. booking.getUser() is definitionally the
                // right owner to pass.
                bookingConfirmationService.confirmBooking(booking.getId(), booking.getUser());
            } catch (Exception ex) {
                // Deliberately caught, not rethrown: the payment.setStatus
                // (SUCCESS) save above must still commit regardless - money
                // was genuinely captured by Razorpay, that fact shouldn't
                // be undone just because the seat could no longer be
                // honored (e.g. its hold well and truly expired by the
                // time this webhook arrived, well after the normal payment
                // window). This is now a "charged but not booked" case that
                // needs a human to look at - logged loudly so it doesn't
                // get lost, same as BookingConfirmationService's own
                // "Contact support - your payment succeeded" messages.
                log.error("Payment captured (bookingId={}) but seat confirmation failed - needs manual follow-up: {}",
                        booking.getId(), ex.getMessage(), ex);
            }
        }
    }
}
