package com.classroom.availability;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Entry point for the Availability Service.
 *
 * <p>Responsibilities:
 * <ol>
 *   <li>Listen on the {@code booking.requested} RabbitMQ queue</li>
 *   <li>Check for time-slot conflicts against an in-memory H2 database</li>
 *   <li>Publish {@code BookingResponse} to {@code booking.confirmed}
 *       or {@code booking.rejected} depending on the outcome</li>
 *   <li>Persist confirmed bookings so future requests can detect conflicts</li>
 * </ol>
 *
 * <p>Runs on port {@code 8082} (configured in {@code application.yml}).
 */
@SpringBootApplication
public class AvailabilityServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(AvailabilityServiceApplication.class, args);
    }
}

