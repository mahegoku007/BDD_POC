package com.classroom.bdd.config;

import io.cucumber.spring.ScenarioScope;
import lombok.Data;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * Scenario-scoped Spring bean that carries state between Cucumber step definitions.
 *
 * <p>{@code @ScenarioScope} means a new instance is created at the start of each
 * scenario and discarded at the end – state never leaks between scenarios.
 *
 * <p>Step definitions inject this bean via {@code @Autowired} to share data
 * produced in {@code Given}/{@code When} steps with assertions in {@code Then} steps.
 */
@Data
@Component
@ScenarioScope
public class ScenarioContext {

    // ── Last HTTP interaction ─────────────────────────────────────────────────

    /** Raw HTTP response from the most recent booking submission. */
    private ResponseEntity<String> lastHttpResponse;

    /** Booking ID extracted from the last successful booking submission. */
    private String lastBookingId;

    /** HTTP status code of the last REST call. */
    private int lastHttpStatus;

    // ── Availability check ────────────────────────────────────────────────────

    /** Last availability status string returned by the availability service. */
    private String lastAvailabilityStatus;

    /** Conflict details from the last availability check (if UNAVAILABLE). */
    private String lastConflictDetails;

    // ── Notification ──────────────────────────────────────────────────────────

    /** Last notification message body observed for positive assertions. */
    private String lastNotificationMessage;

    // ── Messaging ─────────────────────────────────────────────────────────────

    /** Raw JSON body of the last message received from a RabbitMQ queue. */
    private String lastQueueMessageJson;

    /** The queue from which the last message was received. */
    private String lastQueueName;

    // ── Multi-booking scenarios ───────────────────────────────────────────────

    /** Booking IDs submitted during a concurrent / sequential scenario. */
    private final List<String> submittedBookingIds = new ArrayList<>();

    /** Last published event data (for idempotency and re-publish steps). */
    private String lastPublishedEventJson;

    /** The queue the last event was published to. */
    private String lastPublishedQueue;

    // ── Helpers ───────────────────────────────────────────────────────────────

    /** Convenience method to record a submitted bookingId. */
    public void addSubmittedBookingId(String id) {
        submittedBookingIds.add(id);
    }
}

