package com.eventbooking.integration;

import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

/**
 * Base class for "does this actually work under real concurrency" tests.
 *
 * The unit tests elsewhere in this project (SeatLockServiceTest, etc.) mock
 * Redis/the DB and verify SeatLockService's *decision logic* - they prove the
 * code calls the right methods with the right arguments. They do NOT prove
 * that two real threads racing for the same Redis key actually only let one
 * of them win, because a mock can't reproduce a genuine race condition.
 *
 * These integration tests spin up real MySQL and Redis in Docker (via
 * Testcontainers) and fire real concurrent threads at them - this is what
 * actually demonstrates the Week 3 concurrency fix holds up, not just that
 * it's theoretically correct on paper.
 *
 * Containers are static (shared across all subclasses/tests in the same JVM)
 * so they start once per test run, not once per test class.
 */
@Testcontainers
@SpringBootTest
public abstract class AbstractIntegrationTest {

    static final MySQLContainer<?> MYSQL = new MySQLContainer<>(DockerImageName.parse("mysql:8.0"))
            .withDatabaseName("event_booking_test")
            .withUsername("test")
            .withPassword("test")
            .withReuse(true);

    static final GenericContainer<?> REDIS = new GenericContainer<>(DockerImageName.parse("redis:7-alpine"))
            .withExposedPorts(6379)
            .withReuse(true);

    static {
        MYSQL.start();
        REDIS.start();
    }

    @DynamicPropertySource
    static void registerContainerProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", MYSQL::getJdbcUrl);
        registry.add("spring.datasource.username", MYSQL::getUsername);
        registry.add("spring.datasource.password", MYSQL::getPassword);

        registry.add("spring.data.redis.host", REDIS::getHost);
        registry.add("spring.data.redis.port", () -> REDIS.getMappedPort(6379));

        // Point Kafka at a bogus address - these tests don't exercise the
        // async confirmation path, and we don't want a missing Kafka broker
        // to slow down or fail tests that have nothing to do with it.
        registry.add("spring.kafka.bootstrap-servers", () -> "localhost:1");
    }
}
