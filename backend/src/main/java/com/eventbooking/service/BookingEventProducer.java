package com.eventbooking.service;

import com.eventbooking.config.KafkaTopicConfig;
import com.eventbooking.dto.kafka.BookingConfirmedEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

/**
 * Publishing is fire-and-forget from the caller's perspective (BookingService
 * doesn't block waiting for the consumer to finish generating a PDF and
 * "sending" an email) - that decoupling is the entire point of using Kafka
 * here instead of doing ticket generation inline during the payment request.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class BookingEventProducer {

    private final KafkaTemplate<String, BookingConfirmedEvent> kafkaTemplate;

    public void publishBookingConfirmed(BookingConfirmedEvent event) {
        // Keyed by bookingId so all events for the same booking (if this ever
        // grows to publish more than one event per booking) land on the same
        // partition and are processed in order.
        kafkaTemplate.send(KafkaTopicConfig.BOOKING_CONFIRMED_TOPIC, event.getBookingId().toString(), event)
                .whenComplete((result, ex) -> {
                    if (ex != null) {
                        log.warn("Failed to publish BookingConfirmedEvent for bookingId={}: {}",
                                event.getBookingId(), ex.getMessage());
                        // Not rethrown deliberately - the booking itself already succeeded and
                        // committed in MySQL. A missed confirmation email is unfortunate but
                        // must never roll back or fail an already-successful booking.
                    }
                });
    }
}
