package com.classroom.booking;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Entry point for the Booking Service.
 *
 * <p>Responsibilities:
 * <ol>
 *   <li>Exposes a REST endpoint {@code POST /bookings} (consumed by the API Gateway via HTTP)</li>
 *   <li>Assigns a unique {@code bookingId} to every incoming request</li>
 *   <li>Publishes the {@link com.classroom.common.dto.BookingRequest} as a JSON event to
 *       the {@code booking.requested} RabbitMQ queue</li>
 *   <li>Returns {@code 202 Accepted} immediately – processing is asynchronous</li>
 * </ol>
 *
 * <p>Runs on port {@code 8081} (configured in {@code application.yml}).
 */
@SpringBootApplication
public class BookingServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(BookingServiceApplication.class, args);
    }
}

