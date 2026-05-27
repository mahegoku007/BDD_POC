package com.classroom.bdd.steps;

import com.classroom.bdd.config.ScenarioContext;
import com.classroom.common.dto.BookingRequest;
import com.classroom.common.dto.TimeSlot;
import com.classroom.common.util.RabbitMQConstants;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.cucumber.java.After;
import io.cucumber.java.Before;
import io.cucumber.java.en.And;
import io.cucumber.java.en.Given;
import io.cucumber.java.en.Then;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.core.AmqpAdmin;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestTemplate;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;

import static org.awaitility.Awaitility.await;
import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static java.util.concurrent.TimeUnit.SECONDS;

/**
 * Step definitions shared across multiple feature files.
 *
 * <p>Contains:
 * <ul>
 *   <li>Before/After hooks to reset state between scenarios</li>
 *   <li>Common Given steps (classrooms, bookings, RabbitMQ setup)</li>
 *   <li>Common Then steps (notification assertions, queue assertions)</li>
 * </ul>
 */
@Slf4j
@RequiredArgsConstructor
public class CommonStepDefinitions {

    private final ScenarioContext ctx;
    private final RestTemplate    restTemplate;
    private final RabbitTemplate  rabbitTemplate;
    private final AmqpAdmin       rabbitAdmin;
    private final ObjectMapper    objectMapper;

    @Value("${services.availability.url:http://localhost:8082}")
    private String availabilityUrl;

    @Value("${services.notification.url:http://localhost:8083}")
    private String notificationUrl;

    private static final List<String> ALL_QUEUES = List.of(
            RabbitMQConstants.QUEUE_BOOKING_REQUESTED,
            RabbitMQConstants.QUEUE_BOOKING_CONFIRMED,
            RabbitMQConstants.QUEUE_BOOKING_REJECTED);

    // ── Hooks ─────────────────────────────────────────────────────────────────

    @Before
    public void resetStateBeforeScenario() {
        log.info("[BDD-SETUP] Resetting state before scenario");
        ctx.getSubmittedBookingIds().clear();
        // Clear notification log
        try {
            restTemplate.delete(notificationUrl + "/test/notifications");
        } catch (Exception e) {
            log.warn("[BDD-SETUP] Could not clear notification log: {}", e.getMessage());
        }
        // Clear availability store
        try {
            restTemplate.delete(availabilityUrl + "/test/bookings");
        } catch (Exception e) {
            log.warn("[BDD-SETUP] Could not clear availability store: {}", e.getMessage());
        }
    }

    @After
    public void purgeQueuesAfterScenario() {
        ALL_QUEUES.forEach(q -> {
            try { rabbitAdmin.purgeQueue(q, false); } catch (Exception ignored) {}
        });
    }

    // ── Shared Given: classroom availability / existing bookings ──────────────

    /**
     * Ensures no conflicting booking exists for the given classroom/date/slot.
     * The {@code @Before} hook already cleared the availability store; this step
     * documents the precondition.
     */
    @Given("classroom {string} is available on {string} from {string} to {string}")
    public void classroomIsAvailable(String classroomId, String date, String start, String end) {
        log.info("[BDD] Ensuring classroom {} is available on {} from {} to {}",
                classroomId, date, start, end);
        // The @Before hook already cleared the availability store.
        // This step just documents the precondition; the store is already empty.
    }

    /**
     * Alternative phrasing used in {@code messaging_flow.feature}:
     * {@code classroom "X" already has a booking on "date" from "start" to "end"}.
     */
    @Given("classroom {string} already has a booking on {string} from {string} to {string}")
    public void classroomAlreadyHasBookingOn(String classroomId, String date,
                                              String start, String end) {
        classroomHasExistingBooking(classroomId, date, start, end);
    }

    @Given("classroom {string} has an existing booking on {string} from {string} to {string}")
    public void classroomHasExistingBooking(String classroomId, String date,
                                            String start, String end) {
        Map<String, Object> record = new LinkedHashMap<>();
        record.put("bookingId",    "SEED-" + classroomId + "-" + start.replace(":", ""));
        record.put("classroomId",  classroomId);
        record.put("date",         date);
        record.put("startTime",    start);
        record.put("endTime",      end);
        record.put("requestedBy",  "seed@test.com");
        record.put("confirmedAt",  LocalDateTime.now().toString());

        restTemplate.postForEntity(availabilityUrl + "/test/bookings", record, Map.class);
        log.info("[BDD] Seeded existing booking for {} on {} from {} to {}", classroomId, date, start, end);
    }

    // ── Shared Then: notification assertions ─────────────────────────────────

    @Then("a confirmation notification should be logged for {string}")
    public void confirmationNotificationLoggedFor(String email) {
        await().atMost(15, SECONDS).pollInterval(1, SECONDS)
                .alias("Waiting for CONFIRMATION notification for " + email)
                .until(() -> {
                    try {
                        ResponseEntity<List> resp = restTemplate.getForEntity(
                                notificationUrl + "/test/notifications/by-recipient?recipient={e}",
                                List.class, email);
                        List<?> records = resp.getBody();
                        if (records == null || records.isEmpty()) return false;
                        return records.stream().anyMatch(r -> {
                            Map<?, ?> m = (Map<?, ?>) r;
                            return "CONFIRMATION".equals(m.get("type"));
                        });
                    } catch (Exception e) { return false; }
                });

        log.info("[BDD] CONFIRMATION notification verified for {}", email);
    }

    @Then("a rejection notification should be logged for {string}")
    public void rejectionNotificationLoggedFor(String email) {
        await().atMost(15, SECONDS).pollInterval(1, SECONDS)
                .alias("Waiting for REJECTION notification for " + email)
                .until(() -> {
                    try {
                        ResponseEntity<List> resp = restTemplate.getForEntity(
                                notificationUrl + "/test/notifications/by-recipient?recipient={e}",
                                List.class, email);
                        List<?> records = resp.getBody();
                        if (records == null || records.isEmpty()) return false;
                        return records.stream().anyMatch(r -> {
                            Map<?, ?> m = (Map<?, ?>) r;
                            return "REJECTION".equals(m.get("type"));
                        });
                    } catch (Exception e) { return false; }
                });

        log.info("[BDD] REJECTION notification verified for {}", email);
    }

    // ── Shared Then: queue assertions ─────────────────────────────────────────

    @Then("the {string} queue should be empty")
    public void queueShouldBeEmpty(String queueName) {
        await().atMost(10, SECONDS).pollInterval(500, MILLISECONDS)
                .until(() -> getQueueDepth(queueName) == 0);
        log.info("[BDD] Queue '{}' is empty", queueName);
    }

    @Then("all queues should be empty")
    public void allQueuesShouldBeEmpty() {
        ALL_QUEUES.forEach(q -> {
            await().atMost(10, SECONDS)
                    .until(() -> getQueueDepth(q) == 0);
        });
    }

    @And("all RabbitMQ queues should be empty after processing")
    public void allRabbitMQQueuesShouldBeEmptyAfterProcessing() {
        // Give the system time to process all outstanding messages
        await().atMost(15, SECONDS)
                .until(() -> ALL_QUEUES.stream().allMatch(q -> getQueueDepth(q) == 0));
        log.info("[BDD] All queues empty after processing");
    }

    // ── Shared Then: RabbitMQ running ─────────────────────────────────────────

    @Given("RabbitMQ is running on {string} port {string}")
    public void rabbitMqIsRunning(String host, String port) {
        try {
            rabbitAdmin.getQueueProperties(RabbitMQConstants.QUEUE_BOOKING_REQUESTED);
            log.info("[BDD] RabbitMQ is reachable on {}:{}", host, port);
        } catch (Exception e) {
            throw new AssertionError(
                    "RabbitMQ is not reachable on " + host + ":" + port +
                    ". Run 'docker-compose up -d' first.", e);
        }
    }

    // ── Utility ───────────────────────────────────────────────────────────────

    protected int getQueueDepth(String queueName) {
        try {
            Properties props = rabbitAdmin.getQueueProperties(queueName);
            if (props == null) return 0;
            Object count = props.get("QUEUE_MESSAGE_COUNT");
            return count == null ? 0 : ((Number) count).intValue();
        } catch (Exception e) {
            return 0;
        }
    }

    protected BookingRequest buildRequest(String classroomId, String date,
                                          String start, String end, String email) {
        return BookingRequest.builder()
                .classroomId(classroomId)
                .date(LocalDate.parse(date))
                .timeSlot(new TimeSlot(LocalTime.parse(start), LocalTime.parse(end)))
                .requestedBy(email)
                .build();
    }
}






