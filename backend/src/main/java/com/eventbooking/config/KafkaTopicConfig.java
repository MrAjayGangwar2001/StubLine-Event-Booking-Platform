package com.eventbooking.config;

import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;

@Configuration
public class KafkaTopicConfig {

    public static final String BOOKING_CONFIRMED_TOPIC = "booking-confirmed";

    /**
     * Spring Kafka auto-creates this topic on startup if it doesn't already
     * exist (as long as the broker allows auto topic creation, which the
     * Docker Compose Kafka image does by default) - convenient for local dev,
     * though a real deployment would usually provision topics explicitly via
     * infra-as-code instead of relying on this.
     */
    @Bean
    public NewTopic bookingConfirmedTopic() {
        return TopicBuilder.name(BOOKING_CONFIRMED_TOPIC)
                .partitions(3)
                .replicas(1)
                .build();
    }
}
