package com.classroom.notification;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

/**
 * Smoke test – verifies the Spring context loads without a live RabbitMQ broker.
 */
@SpringBootTest
@ActiveProfiles("test")
class NotificationServiceApplicationTests {

    @Test
    void contextLoads() {
        // passes if all beans wire correctly under the test profile
    }
}

