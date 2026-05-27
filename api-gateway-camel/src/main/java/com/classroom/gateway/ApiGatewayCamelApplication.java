package com.classroom.gateway;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Entry point for the API Gateway (Apache Camel layer).
 *
 * <p>This service is the single entry point for all external clients.
 * It exposes REST endpoints via Apache Camel's REST DSL (no traditional
 * {@code @RestController}) and routes requests downstream.
 *
 * <p>Flow overview:
 * <pre>
 *   Client
 *     │  POST /bookings (JSON)
 *     ▼
 *   Camel REST DSL (platform-http on port 8080)
 *     │  validates, enriches, logs
 *     ▼
 *   service-booking (:8081/bookings) via HTTP
 *     │  returns 202 Accepted + BookingRequest (PENDING)
 *     ▼
 *   Client receives 202 Accepted
 * </pre>
 *
 * <p>Runs on port {@code 8080} (configured in {@code application.yml}).
 */
@SpringBootApplication
public class ApiGatewayCamelApplication {

    public static void main(String[] args) {
        SpringApplication.run(ApiGatewayCamelApplication.class, args);
    }
}

