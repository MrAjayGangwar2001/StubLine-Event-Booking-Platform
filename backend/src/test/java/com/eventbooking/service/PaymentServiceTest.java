package com.eventbooking.service;

import com.eventbooking.dto.payment.VerifyPaymentRequest;
import com.eventbooking.entity.Payment;
import com.eventbooking.entity.PaymentStatus;
import com.eventbooking.exception.BadRequestException;
import com.eventbooking.repository.PaymentRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PaymentServiceTest {

    @Mock private PaymentRepository paymentRepository;
    @Mock private RazorpayService razorpayService;

    @InjectMocks
    private PaymentService paymentService;

    private Payment pendingPayment;
    private VerifyPaymentRequest request;

    @BeforeEach
    void setUp() {
        pendingPayment = Payment.builder()
                .id(1L)
                .providerOrderId("order_ABC123")
                .amount(new BigDecimal("1500"))
                .status(PaymentStatus.PENDING)
                .idempotencyKey("key-1")
                .build();

        request = new VerifyPaymentRequest();
        request.setBookingId(10L);
        request.setRazorpayOrderId("order_ABC123");
        request.setRazorpayPaymentId("pay_XYZ789");
        request.setRazorpaySignature("valid_signature");
    }

    @Test
    void verifyPayment_marksSuccess_whenSignatureIsValid() {
        when(paymentRepository.findByBookingId(10L)).thenReturn(Optional.of(pendingPayment));
        when(razorpayService.verifySignature("order_ABC123", "pay_XYZ789", "valid_signature")).thenReturn(true);
        when(paymentRepository.save(any(Payment.class))).thenAnswer(inv -> inv.getArgument(0));

        Long bookingId = paymentService.verifyPayment(request);

        assertThat(bookingId).isEqualTo(10L);
        assertThat(pendingPayment.getStatus()).isEqualTo(PaymentStatus.SUCCESS);
        assertThat(pendingPayment.getProviderPaymentId()).isEqualTo("pay_XYZ789");
    }

    @Test
    void verifyPayment_marksFailed_andThrows_whenSignatureIsInvalid() {
        when(paymentRepository.findByBookingId(10L)).thenReturn(Optional.of(pendingPayment));
        when(razorpayService.verifySignature("order_ABC123", "pay_XYZ789", "valid_signature")).thenReturn(false);

        assertThatThrownBy(() -> paymentService.verifyPayment(request))
                .isInstanceOf(BadRequestException.class);

        assertThat(pendingPayment.getStatus()).isEqualTo(PaymentStatus.FAILED);
        verify(paymentRepository).save(pendingPayment);
    }

    @Test
    void verifyPayment_rejects_whenOrderIdDoesNotMatchStoredPayment() {
        // Client claims a different order id than what we actually created for this booking -
        // must never call verifySignature at all in this case, let alone trust it.
        request.setRazorpayOrderId("order_SOMETHING_ELSE");
        when(paymentRepository.findByBookingId(10L)).thenReturn(Optional.of(pendingPayment));

        assertThatThrownBy(() -> paymentService.verifyPayment(request))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("does not match");

        verify(razorpayService, never()).verifySignature(any(), any(), any());
    }

    @Test
    void verifyPayment_isIdempotent_whenAlreadyMarkedSuccess() {
        pendingPayment.setStatus(PaymentStatus.SUCCESS);
        when(paymentRepository.findByBookingId(10L)).thenReturn(Optional.of(pendingPayment));

        Long bookingId = paymentService.verifyPayment(request);

        assertThat(bookingId).isEqualTo(10L);
        // Must not re-verify or re-save on a retry of an already-successful call
        verify(razorpayService, never()).verifySignature(any(), any(), any());
        verify(paymentRepository, never()).save(any());
    }
}
