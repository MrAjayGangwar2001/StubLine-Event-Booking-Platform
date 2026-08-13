package com.eventbooking.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.client.RestTemplate;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.util.HexFormat;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Signature verification is the single most security-critical piece of the
 * whole payment flow - it's the only thing standing between "this payment is
 * real" and "the client just told us it succeeded". These tests don't hit
 * Razorpay's real API (that's what createOrder() would need, mocked/integration-
 * tested separately); they specifically exercise the HMAC check in isolation.
 */
class RazorpayServiceTest {

    private static final String SECRET = "test_secret_key_12345";

    private final RazorpayService razorpayService =
            new RazorpayService(new RestTemplate(), new ObjectMapper());

    {
        ReflectionTestUtils.setField(razorpayService, "keySecret", SECRET);
        ReflectionTestUtils.setField(razorpayService, "keyId", "test_key_id");
    }

    @Test
    void verifySignature_returnsTrue_forACorrectlyComputedSignature() {
        String orderId = "order_ABC123";
        String paymentId = "pay_XYZ789";
        String validSignature = computeHmac(orderId + "|" + paymentId, SECRET);

        assertThat(razorpayService.verifySignature(orderId, paymentId, validSignature)).isTrue();
    }

    @Test
    void verifySignature_returnsFalse_whenSignatureIsTamperedWith() {
        String orderId = "order_ABC123";
        String paymentId = "pay_XYZ789";
        String validSignature = computeHmac(orderId + "|" + paymentId, SECRET);
        // Flip one character - simulates a forged/corrupted callback
        String tamperedSignature = validSignature.substring(0, validSignature.length() - 1) + "0";

        assertThat(razorpayService.verifySignature(orderId, paymentId, tamperedSignature)).isFalse();
    }

    @Test
    void verifySignature_returnsFalse_whenPaymentIdDoesNotMatchWhatWasSigned() {
        // Attacker takes a genuine signature from ONE payment and tries to
        // reuse it while claiming a different payment id.
        String orderId = "order_ABC123";
        String signatureForDifferentPayment = computeHmac(orderId + "|pay_ORIGINAL", SECRET);

        assertThat(razorpayService.verifySignature(orderId, "pay_SUBSTITUTED", signatureForDifferentPayment)).isFalse();
    }

    @Test
    void verifySignature_returnsFalse_whenSignedWithWrongSecret() {
        // Simulates a signature computed with a different Razorpay account's
        // secret - must never validate against OUR key.
        String orderId = "order_ABC123";
        String paymentId = "pay_XYZ789";
        String signatureFromWrongSecret = computeHmac(orderId + "|" + paymentId, "some_other_secret");

        assertThat(razorpayService.verifySignature(orderId, paymentId, signatureFromWrongSecret)).isFalse();
    }

    private String computeHmac(String payload, String secret) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            byte[] hash = mac.doFinal(payload.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
