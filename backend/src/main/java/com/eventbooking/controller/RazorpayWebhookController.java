package com.eventbooking.controller;

import com.eventbooking.exception.BadRequestException;
import com.eventbooking.service.RazorpayWebhookService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Deliberately outside /api/auth or anything JWT-protected - Razorpay's
 * servers call this directly, with no user session at all. Trust comes
 * entirely from the HMAC signature check inside RazorpayWebhookService, not
 * from Spring Security (see SecurityConfig - this path is explicitly
 * permitAll for exactly this reason).
 */
@RestController
@RequestMapping("/api/webhooks")
@RequiredArgsConstructor
@Slf4j
public class RazorpayWebhookController {

    private final RazorpayWebhookService webhookService;

    @PostMapping(value = "/razorpay", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<String> handleRazorpayWebhook(
            @RequestBody String rawBody,
            @RequestHeader(value = "X-Razorpay-Signature", required = false) String signature) {
        try {
            webhookService.handleEvent(rawBody, signature);
            return ResponseEntity.ok("ok");
        } catch (BadRequestException ex) {
            // Bad/missing signature or malformed payload - not something a
            // retry will fix, so a 4xx here is correct (tells Razorpay's
            // dashboard this delivery genuinely failed, not "try again").
            log.warn("Rejected Razorpay webhook: {}", ex.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("rejected");
        } catch (Exception ex) {
            // Unexpected failure (DB hiccup, etc) - a 5xx here is deliberate,
            // not swallowed to 200: Razorpay retries non-2xx responses with
            // backoff for a while, which is exactly what should happen for
            // a transient error. Returning 200 here would silently drop a
            // payment confirmation forever.
            log.error("Error processing Razorpay webhook - Razorpay will retry: {}", ex.getMessage(), ex);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("error");
        }
    }
}
