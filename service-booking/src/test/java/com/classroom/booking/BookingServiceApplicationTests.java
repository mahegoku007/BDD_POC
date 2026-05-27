package com.classroom.booking;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

/**
 * Smoke test – verifies the Spring application context loads without errors.
 *
 * <p>Uses the {@code test} profile which configures an in-memory RabbitMQ
 * connection via {@code application-test.yml} (if present) or disables
 * auto-configuration of the AMQP connection factory.
 */
@SpringBootTest
@ActiveProfiles("test")
class BookingServiceApplicationTests {

    @Test
    void contextLoads() {
        // If this passes, all beans are wired correctly
    }
}

