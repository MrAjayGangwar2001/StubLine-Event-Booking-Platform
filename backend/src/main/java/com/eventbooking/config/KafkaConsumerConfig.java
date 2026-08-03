package com.eventbooking.config;

import com.eventbooking.dto.kafka.BookingConfirmedEvent;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.springframework.boot.autoconfigure.kafka.KafkaProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.core.ConsumerFactory;
import org.springframework.kafka.core.DefaultKafkaConsumerFactory;
import org.springframework.kafka.support.serializer.ErrorHandlingDeserializer;
import org.springframework.kafka.support.serializer.JsonDeserializer;

@Configuration
public class KafkaConsumerConfig {

    @Bean
    public ConsumerFactory<String, BookingConfirmedEvent> consumerFactory(KafkaProperties kafkaProperties) {
        JsonDeserializer<BookingConfirmedEvent> jsonDeserializer = new JsonDeserializer<>(BookingConfirmedEvent.class);
        jsonDeserializer.setRemoveTypeHeaders(false);
        jsonDeserializer.addTrustedPackages("com.eventbooking.dto.kafka");

        return new DefaultKafkaConsumerFactory<>(
                kafkaProperties.buildConsumerProperties(null),
                new StringDeserializer(),
                // Wrapping in ErrorHandlingDeserializer so a single malformed/incompatible
                // message logs and gets skipped instead of killing the whole listener
                // container (which would silently stop processing every future message too).
                new ErrorHandlingDeserializer<>(jsonDeserializer)
        );
    }

    @Bean
    public ConcurrentKafkaListenerContainerFactory<String, BookingConfirmedEvent> kafkaListenerContainerFactory(
            ConsumerFactory<String, BookingConfirmedEvent> consumerFactory) {
        ConcurrentKafkaListenerContainerFactory<String, BookingConfirmedEvent> factory =
                new ConcurrentKafkaListenerContainerFactory<>();
        factory.setConsumerFactory(consumerFactory);
        return factory;
    }
}
