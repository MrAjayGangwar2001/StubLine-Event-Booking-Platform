package com.eventbooking.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;

@Configuration
public class RedisConfig {

    /**
     * Plain String key/value template - the seat lock is just
     * "seat_lock:{eventSeatId}" -> "{userId}" with a TTL, so there's no need
     * for JSON (de)serialization overhead here.
     */
    @Bean
    public StringRedisTemplate stringRedisTemplate(RedisConnectionFactory connectionFactory) {
        return new StringRedisTemplate(connectionFactory);
    }
}
