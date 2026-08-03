package com.eventbooking.config;

import com.eventbooking.dto.kafka.BookingConfirmedEvent;
import org.springframework.boot.autoconfigure.kafka.KafkaProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.core.ProducerFactory;

/**
 * Spring Boot's autoconfigured KafkaTemplate is generically typed
 * KafkaTemplate<Object, Object>, which doesn't reliably autowire into a field
 * declared as KafkaTemplate<String, BookingConfirmedEvent> (generic erasure
 * makes Spring's type matching unreliable here). Defining our own
 * properly-typed bean sidesteps that ambiguity entirely, while still reusing
 * every spring.kafka.producer.* property already set in application.yml.
 */
@Configuration
public class KafkaProducerConfig {

    @Bean
    public ProducerFactory<String, BookingConfirmedEvent> producerFactory(KafkaProperties kafkaProperties) {
        return new DefaultKafkaProducerFactory<>(kafkaProperties.buildProducerProperties(null));
    }

    @Bean
    public KafkaTemplate<String, BookingConfirmedEvent> kafkaTemplate(
            ProducerFactory<String, BookingConfirmedEvent> producerFactory) {
        return new KafkaTemplate<>(producerFactory);
    }
}
