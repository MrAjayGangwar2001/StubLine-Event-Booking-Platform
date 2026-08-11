package com.eventbooking.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.HexFormat;
import java.util.Map;

/**
 * Thin wrapper around Razorpay's REST API (chosen over pulling in their Java
 * SDK, since the two things actually needed - creating an order, and
 * verifying a payment signature - are each a handful of lines with
 * RestTemplate + standard HMAC, and doing it this way makes the security-
 * critical signature check fully visible instead of hidden inside a
 * third-party library).
 *
 * Needs real (free) test-mode keys to actually work: sign up at
 * https://dashboard.razorpay.com, toggle "Test Mode", and put the key id /
 * secret into RAZORPAY_KEY_ID / RAZORPAY_KEY_SECRET env vars. No real money
 * moves in test mode - it's built exactly for this kind of integration testing.
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class RazorpayService {

    private static final String ORDERS_URL = "https://api.razorpay.com/v1/orders";

    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;

    @Value("${app.razorpay.key-id}")
    private String keyId;

    @Value("${app.razorpay.key-secret}")
    private String keySecret;

    // A DIFFERENT secret from key-secret above - set separately in the
    // Razorpay dashboard when configuring the webhook URL. Never reuse
    // key-secret here; they're deliberately independent so a leak of one
    // doesn't compromise the other.
    @Value("${app.razorpay.webhook-secret}")
    private String webhookSecret;

    /**
     * Creates a Razorpay order for the given amount and returns its order id.
     * This has to happen server-side (never trust a client-supplied amount) -
     * the order id is what ties the amount to a specific checkout attempt,
     * and is one half of what gets signature-verified after payment.
     */
    public String createOrder(BigDecimal amountInRupees, String receiptId) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBasicAuth(keyId, keySecret);

        // Razorpay expects the amount in the smallest currency unit (paise for INR)
        long amountInPaise = amountInRupees.movePointRight(2).longValueExact();

        // Razorpay hard-rejects any receipt over 40 characters with a 400
        // (learned this the hard way - see BookingService's comment on its
        // caller). Truncating here too, defensively, so this method is safe
        // regardless of what any future caller passes in.
        String safeReceipt = receiptId.length() > 40 ? receiptId.substring(0, 40) : receiptId;

        Map<String, Object> body = Map.of(
                "amount", amountInPaise,
                "currency", "INR",
                "receipt", safeReceipt
        );

        HttpEntity<Map<String, Object>> request = new HttpEntity<>(body, headers);

        try {
            String response = restTemplate.postForObject(ORDERS_URL, request, String.class);
            JsonNode json = objectMapper.readTree(response);
            return json.get("id").asText();
        } catch (org.springframework.web.client.HttpClientErrorException ex) {
            // 401 here almost always means keyId/keySecret are blank or wrong
            // (e.g. RAZORPAY_KEY_ID/RAZORPAY_KEY_SECRET not actually present
            // in the process env - check how the app is being started, not
            // just the .env file's contents).
            log.error("Razorpay rejected the order request: {} {} - body: {}",
                    ex.getStatusCode(), ex.getStatusText(), ex.getResponseBodyAsString());
            throw new RuntimeException("Could not initiate payment. Please try again.", ex);
        } catch (Exception ex) {
            log.error("Failed to create Razorpay order: {}", ex.getMessage(), ex);
            throw new RuntimeException("Could not initiate payment. Please try again.", ex);
        }
    }

    /**
     * Verifies that a payment callback actually came from Razorpay and wasn't
     * forged/tampered with by the client. Razorpay signs
     * "{order_id}|{payment_id}" with HMAC-SHA256 using the account's secret
     * key; if our own computed signature doesn't match what the client sent,
     * the payment must not be trusted, no matter what the client claims.
     */
    public boolean verifySignature(String orderId, String paymentId, String signature) {
        try {
            String payload = orderId + "|" + paymentId;
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(keySecret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            byte[] hash = mac.doFinal(payload.getBytes(StandardCharsets.UTF_8));
            String computedSignature = HexFormat.of().formatHex(hash);
            return computedSignature.equals(signature);
        } catch (Exception ex) {
            log.error("Signature verification failed unexpectedly: {}", ex.getMessage());
            return false; // fail closed - never treat an error as a valid payment
        }
    }

    /**
     * Same HMAC-SHA256 idea as verifySignature() above, but for a webhook
     * call instead of a browser checkout callback - different payload
     * (Razorpay signs the ENTIRE raw request body, not just "orderId|paymentId")
     * and a different secret (webhookSecret, configured separately in the
     * Razorpay dashboard when setting up the webhook URL - never the same
     * value as key-secret). Without this check, anyone who discovers the
     * webhook URL could POST a fake "payment.captured" event and get a
     * booking confirmed for free - this is the only thing standing between
     * this public, unauthenticated endpoint and exactly that.
     */
    public boolean verifyWebhookSignature(String rawRequestBody, String signature) {
        if (webhookSecret == null || webhookSecret.isBlank()) {
            log.error("RAZORPAY_WEBHOOK_SECRET is not configured - refusing to trust any webhook call");
            return false;
        }
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(webhookSecret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            byte[] hash = mac.doFinal(rawRequestBody.getBytes(StandardCharsets.UTF_8));
            String computedSignature = HexFormat.of().formatHex(hash);
            return computedSignature.equals(signature);
        } catch (Exception ex) {
            log.error("Webhook signature verification failed unexpectedly: {}", ex.getMessage());
            return false; // fail closed here too
        }
    }
}
