package com.eventbooking.service;

import com.eventbooking.config.KafkaTopicConfig;
import com.eventbooking.dto.kafka.BookingConfirmedEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Service;

import java.nio.file.Path;

/**
 * Runs entirely off the request thread that handled the payment - this is
 * the actual payoff of using Kafka here. If PDF generation were done inline
 * during POST /verify-payment, every user would wait on it before getting a
 * response; here, checkout completes instantly and this consumer does the
 * (comparatively slow) ticket + email work in the background, at its own pace.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class BookingConfirmationConsumer {

    private final TicketService ticketService;
    private final EmailService emailService;

    @KafkaListener(topics = KafkaTopicConfig.BOOKING_CONFIRMED_TOPIC, groupId = "event-booking-group")
    public void onBookingConfirmed(BookingConfirmedEvent event) {
        log.info("Processing BookingConfirmedEvent for bookingId={}", event.getBookingId());

        try {
            Path ticketPath = ticketService.generateTicketPdf(event);
            emailService.sendBookingConfirmation(event, ticketPath);
        } catch (Exception ex) {
            // Deliberately caught and logged rather than rethrown: letting this
            // throw would trigger Kafka's default retry behavior and re-deliver
            // the same message repeatedly, potentially forever, for something
            // like a bad QR encoding that will never succeed on retry. The
            // booking itself is already confirmed in MySQL regardless - a
            // failed ticket/email here is unfortunate but not something that
            // should block or loop the consumer.
            log.error("Failed to process BookingConfirmedEvent for bookingId={}: {}",
                    event.getBookingId(), ex.getMessage(), ex);
        }
    }
}
