package com.classroom.availability;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

/**
 * Smoke test – verifies the full Spring context loads without errors.
 * Uses the {@code test} profile to disable real RabbitMQ connection.
 */
@SpringBootTest
@ActiveProfiles("test")
class AvailabilityServiceApplicationTests {

    @Test
    void contextLoads() {
        // passes if all beans are wired and H2 schema is created successfully
    }
}

