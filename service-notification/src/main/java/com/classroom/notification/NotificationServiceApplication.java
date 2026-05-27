package com.classroom.notification;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Entry point for the Notification Service.
 *
 * <p>Responsibilities:
 * <ol>
 *   <li>Listen on the {@code booking.confirmed} queue → send a confirmation notification</li>
 *   <li>Listen on the {@code booking.rejected} queue  → send a rejection notification</li>
 *   <li>Persist every sent notification in an in-memory log for traceability
 *       and integration-test assertions</li>
 * </ol>
 *
 * <p>Runs on port {@code 8083} (configured in {@code application.yml}).
 */
@SpringBootApplication
public class NotificationServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(NotificationServiceApplication.class, args);
    }
}

