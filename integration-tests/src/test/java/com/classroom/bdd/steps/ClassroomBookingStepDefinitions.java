package com.classroom.bdd.steps;

import com.classroom.bdd.config.ScenarioContext;
import com.classroom.common.dto.BookingRequest;
import com.classroom.common.dto.TimeSlot;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.cucumber.java.en.Given;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestTemplate;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static java.util.concurrent.TimeUnit.SECONDS;

/**
 * Step definitions for {@code classroom_booking.feature}.
 *
 * <p>These steps submit booking requests through the API Gateway and assert
 * the final outcome via the Notification Service's test endpoint.
 */
@Slf4j
@RequiredArgsConstructor
public class ClassroomBookingStepDefinitions {

    private final ScenarioContext ctx;
    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;

    @Value("${services.gateway.url:http://localhost:8080}")
    private String gatewayUrl;

    @Value("${services.notification.url:http://localhost:8083}")
    private String notificationUrl;

    // ── Background steps ──────────────────────────────────────────────────────

    @Given("the booking system is up and running")
    public void theBookingSystemIsUpAndRunning() {
        ResponseEntity<String> health = restTemplate.getForEntity(
                gatewayUrl + "/actuator/health", String.class);
        assertThat(health.getStatusCode().is2xxSuccessful())
                .as("API Gateway /actuator/health should return 2xx").isTrue();
        log.info("[BDD] Booking system health check passed");
    }

    @Given("the following classrooms exist:")
    public void theFollowingClassroomsExist(List<Map<String, String>> rows) {
        // Classrooms are implicitly available in our in-memory store.
        // This step documents preconditions but requires no action.
        log.info("[BDD] Acknowledged {} classroom(s) in Background", rows.size());
    }

    // ── When: Submit booking ──────────────────────────────────────────────────

    @When("user {string} books classroom {string} on {string} from {string} to {string}")
    public void userBooksClassroom(String email, String classroomId,
                                   String date, String start, String end) {
        submitBookingViaGateway(classroomId, date, start, end, email);
    }

    @When("user {string} tries to book classroom {string} on {string} from {string} to {string}")
    public void userTriesToBookClassroom(String email, String classroomId,
                                         String date, String start, String end) {
        submitBookingViaGateway(classroomId, date, start, end, email);
    }

    @When("user {string} requests to book classroom {string} on {string} from {string} to {string}")
    public void userRequestsToBookClassroom(String email, String classroomId,
                                             String date, String start, String end) {
        submitBookingViaGateway(classroomId, date, start, end, email);
    }

    // ── Given: Pre-condition availability status (Scenario Outline) ───────────

    @Given("classroom {string} availability status is {string} on {string} from {string} to {string}")
    public void classroomAvailabilityStatusIs(String classroomId, String status,
                                               String date, String start, String end)
            throws Exception {
        if ("UNAVAILABLE".equalsIgnoreCase(status)) {
            Map<String, Object> record = new LinkedHashMap<>();
            record.put("bookingId",   "PRE-SEED-" + classroomId);
            record.put("classroomId", classroomId);
            record.put("date",        date);
            record.put("startTime",   start);
            record.put("endTime",     end);
            record.put("requestedBy", "preseed@test.com");
            record.put("confirmedAt", LocalDateTime.now().toString());
            restTemplate.postForEntity("http://localhost:8082/test/bookings", record, Map.class);
            log.info("[BDD] Pre-seeded UNAVAILABLE slot for {}", classroomId);
        }
        // AVAILABLE: store is already clean (cleared in @Before hook)
    }

    // ── Then: Booking status ──────────────────────────────────────────────────

    @Then("the booking status should be {string}")
    public void theBookingStatusShouldBe(String expectedStatus) {
        String bookingId = ctx.getLastBookingId();
        assertThat(bookingId).as("bookingId should have been captured").isNotNull();

        log.info("[BDD] Polling for status {} for bookingId {}", expectedStatus, bookingId);

        await().atMost(15, SECONDS).pollInterval(1, SECONDS)
                .alias("Waiting for booking " + bookingId + " to reach status " + expectedStatus)
                .until(() -> getNotificationTypeFor(bookingId, expectedStatus));
    }

    // ── Then: Notification sent ───────────────────────────────────────────────

    @Then("a confirmation notification should be sent to {string}")
    public void confirmationNotificationSentTo(String email) {
        // Delegate to the shared "logged for" step via the notification endpoint
        await().atMost(15, SECONDS).pollInterval(1, SECONDS)
                .until(() -> hasNotificationOfType(email, "CONFIRMATION"));
        log.info("[BDD] Confirmation notification found for {}", email);
    }

    @Then("a rejection notification should be sent to {string}")
    public void rejectionNotificationSentTo(String email) {
        await().atMost(15, SECONDS).pollInterval(1, SECONDS)
                .until(() -> hasNotificationOfType(email, "REJECTION"));
        log.info("[BDD] Rejection notification found for {}", email);
    }

    @Then("the rejection reason should be {string}")
    public void theRejectionReasonShouldBe(String expectedReason) {
        // Fetch the last notification for the booking and verify the message contains the reason
        String bookingId = ctx.getLastBookingId();
        await().atMost(15, SECONDS).pollInterval(1, SECONDS)
                .until(() -> {
                    try {
                        ResponseEntity<List> resp = restTemplate.getForEntity(
                                notificationUrl + "/test/notifications/by-booking?bookingId={id}",
                                List.class, bookingId);
                        List<?> records = resp.getBody();
                        if (records == null || records.isEmpty()) return false;
                        return records.stream().anyMatch(r -> {
                            Map<?, ?> m = (Map<?, ?>) r;
                            String message = (String) m.get("message");
                            return message != null && message.contains(expectedReason);
                        });
                    } catch (Exception e) { return false; }
                });
        log.info("[BDD] Rejection reason '{}' verified", expectedReason);
    }

    @Then("the event {string} should be published to RabbitMQ")
    public void theEventShouldBePublishedToRabbitMQ(String eventType) {
        // By the time this step runs, the notification service has already
        // consumed the event (and it's gone from the queue).
        // We verify the event "was published" by checking the notification log.
        String notificationType = "booking.confirmed".equals(eventType) ? "CONFIRMATION" : "REJECTION";
        await().atMost(15, SECONDS).pollInterval(1, SECONDS)
                .until(() -> {
                    try {
                        ResponseEntity<Map> summary = restTemplate.getForEntity(
                                notificationUrl + "/test/notifications/summary", Map.class);
                        Map<?, ?> body = summary.getBody();
                        if (body == null) return false;
                        Number count = (Number) body.get(
                                "CONFIRMATION".equals(notificationType) ? "confirmations" : "rejections");
                        return count != null && count.intValue() > 0;
                    } catch (Exception e) { return false; }
                });
        log.info("[BDD] Event {} verified via notification log", eventType);
    }

    // ── Private helpers ───────────────────────────────────────────────────────

    private void submitBookingViaGateway(String classroomId, String date,
                                          String start, String end, String email) {
        BookingRequest body = BookingRequest.builder()
                .classroomId(classroomId)
                .date(LocalDate.parse(date))
                .timeSlot(new TimeSlot(LocalTime.parse(start), LocalTime.parse(end)))
                .requestedBy(email)
                .build();

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);

        try {
            ResponseEntity<String> response = restTemplate.postForEntity(
                    gatewayUrl + "/bookings",
                    new HttpEntity<>(body, headers),
                    String.class);

            ctx.setLastHttpStatus(response.getStatusCode().value());
            ctx.setLastHttpResponse(response);

            // Extract bookingId from response JSON
            if (response.getBody() != null) {
                JsonNode json = objectMapper.readTree(response.getBody());
                JsonNode idNode = json.path("bookingId");
                if (!idNode.isMissingNode() && !idNode.isNull()) {
                    ctx.setLastBookingId(idNode.asText());
                    ctx.addSubmittedBookingId(idNode.asText());
                    log.info("[BDD] Submitted booking | bookingId={} | status={}",
                            ctx.getLastBookingId(), response.getStatusCode());
                }
            }

        } catch (HttpClientErrorException e) {
            ctx.setLastHttpStatus(e.getStatusCode().value());
            log.warn("[BDD] Booking request returned {}: {}", e.getStatusCode(), e.getMessage());
        } catch (Exception e) {
            throw new RuntimeException("Failed to submit booking via gateway", e);
        }
    }

    private boolean getNotificationTypeFor(String bookingId, String expectedStatus) {
        String expectedType = "CONFIRMED".equals(expectedStatus) ? "CONFIRMATION" : "REJECTION";
        try {
            ResponseEntity<List> resp = restTemplate.getForEntity(
                    notificationUrl + "/test/notifications/by-booking?bookingId={id}",
                    List.class, bookingId);
            List<?> records = resp.getBody();
            if (records == null || records.isEmpty()) return false;
            return records.stream().anyMatch(r -> expectedType.equals(((Map<?, ?>) r).get("type")));
        } catch (Exception e) { return false; }
    }

    private boolean hasNotificationOfType(String email, String type) {
        try {
            ResponseEntity<List> resp = restTemplate.getForEntity(
                    notificationUrl + "/test/notifications/by-recipient?recipient={e}",
                    List.class, email);
            List<?> records = resp.getBody();
            if (records == null) return false;
            return records.stream().anyMatch(r -> type.equals(((Map<?, ?>) r).get("type")));
        } catch (Exception e) { return false; }
    }
}





