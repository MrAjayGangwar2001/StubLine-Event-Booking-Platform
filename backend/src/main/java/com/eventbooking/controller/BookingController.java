package com.eventbooking.controller;

import com.eventbooking.dto.booking.BookingResponse;
import com.eventbooking.dto.booking.CreateBookingRequest;
import com.eventbooking.dto.payment.CreateBookingResponse;
import com.eventbooking.dto.payment.VerifyPaymentRequest;
import com.eventbooking.service.BookingService;
import com.eventbooking.service.PaymentService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;
import java.util.List;

@RestController
@RequestMapping("/api/bookings")
@RequiredArgsConstructor
public class BookingController {

    private final BookingService bookingService;
    private final PaymentService paymentService;

    /**
     * Creates a PENDING booking + Razorpay order. The booking is NOT
     * confirmed yet - the frontend uses the returned order details to open
     * Razorpay Checkout, then calls /verify-payment once the user pays.
     */
    @PostMapping
    public ResponseEntity<CreateBookingResponse> createBooking(@Valid @RequestBody CreateBookingRequest request) {
        return ResponseEntity.ok(bookingService.createBooking(request));
    }

    /**
     * Called after Razorpay Checkout succeeds client-side. Verifies the
     * payment signature server-side (never trust the client's word alone),
     * then actually confirms the booking and books the seats.
     */
    @PostMapping("/verify-payment")
    public ResponseEntity<BookingResponse> verifyPayment(@Valid @RequestBody VerifyPaymentRequest request) {
        Long bookingId = paymentService.verifyPayment(request);
        BookingResponse response = bookingService.confirmBookingAfterPayment(bookingId);
        return ResponseEntity.ok(response);
    }

    /**
     * User backed out of the checkout modal before paying - releases their
     * seat holds immediately instead of leaving them locked for the rest of
     * the payment window.
     */
    @PostMapping("/{id}/cancel-pending")
    public ResponseEntity<Void> cancelPendingBooking(@PathVariable Long id) {
        bookingService.cancelPendingBooking(id);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/my")
    public ResponseEntity<List<BookingResponse>> getMyBookings() {
        return ResponseEntity.ok(bookingService.getMyBookings());
    }

    @PostMapping("/{id}/cancel")
    public ResponseEntity<BookingResponse> cancelBooking(@PathVariable Long id) {
        return ResponseEntity.ok(bookingService.cancelBooking(id));
    }

    /**
     * Lets a user view/download their own invoice from inside the app,
     * instead of the confirmation email being the only place it ever shows
     * up - important for anyone who signed up with a password/OTP and
     * doesn't have reliable access to whatever inbox is on file (typo'd
     * address, spam-filtered, etc). Regenerates the PDF fresh each time
     * rather than depending on the copy already saved to disk after
     * payment - see BookingService.downloadInvoice() for why.
     */
    @GetMapping("/{id}/invoice")
    public ResponseEntity<byte[]> downloadInvoice(@PathVariable Long id) throws IOException {
        byte[] pdf = bookingService.downloadInvoice(id);
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_PDF)
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=StubLine-Invoice-" + id + ".pdf")
                .body(pdf);
    }
}
