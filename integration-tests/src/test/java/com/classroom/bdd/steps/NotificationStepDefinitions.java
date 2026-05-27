package com.classroom.bdd.steps;

import com.classroom.bdd.config.ScenarioContext;
import com.classroom.common.dto.BookingResponse;
import com.classroom.common.dto.TimeSlot;
import com.classroom.common.enums.BookingStatus;
import com.classroom.common.util.RabbitMQConstants;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.cucumber.datatable.DataTable;
import io.cucumber.java.en.Given;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestTemplate;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static java.util.concurrent.TimeUnit.SECONDS;

/**
 * Step definitions for {@code notification.feature}.
 *
 * <p>These steps publish {@link BookingResponse} events directly onto the
 * {@code booking.confirmed} / {@code booking.rejected} queues and then assert
 * that the Notification Service processes them correctly.
 */
@Slf4j
@RequiredArgsConstructor
public class NotificationStepDefinitions {

    private final ScenarioContext ctx;
    private final RabbitTemplate  rabbitTemplate;
    private final RestTemplate    restTemplate;
    private final ObjectMapper    objectMapper;

    @Value("${services.notification.url:http://localhost:8083}")
    private String notificationUrl;

    // ── Given ─────────────────────────────────────────────────────────────────

    @Given("the notification service is running")
    public void theNotificationServiceIsRunning() {
        ResponseEntity<String> health = restTemplate.getForEntity(
                notificationUrl + "/actuator/health", String.class);
        assertThat(health.getStatusCode().is2xxSuccessful())
                .as("Notification service /actuator/health").isTrue();
        log.info("[BDD] Notification service health check passed");
    }

    @Given("the notification log is empty")
    public void theNotificationLogIsEmpty() {
        restTemplate.delete(notificationUrl + "/test/notifications");
        log.info("[BDD] Notification log cleared");
    }

    @Given("a {string} event is published with:")
    public void aBookingEventIsPublishedWith(String eventType, DataTable table) throws Exception {
        Map<String, String> row = table.asMap();
        BookingResponse response = buildResponseFromMap(row, resolveStatus(eventType));
        String queue = resolveQueue(eventType);
        String routingKey = resolveRoutingKey(eventType);

        rabbitTemplate.convertAndSend(RabbitMQConstants.EXCHANGE, routingKey, response);

        ctx.setLastPublishedEventJson(objectMapper.writeValueAsString(response));
        ctx.setLastPublishedQueue(queue);

        log.info("[BDD] Published {} event for bookingId={}", eventType, response.getBookingId());
    }

    @Given("the same event is published again")
    public void theSameEventIsPublishedAgain() throws Exception {
        assertThat(ctx.getLastPublishedEventJson()).isNotNull();
        BookingResponse response = objectMapper.readValue(ctx.getLastPublishedEventJson(), BookingResponse.class);
        String routingKey = resolveRoutingKey(
                response.getStatus() == BookingStatus.CONFIRMED ? "booking.confirmed" : "booking.rejected");

        rabbitTemplate.convertAndSend(RabbitMQConstants.EXCHANGE, routingKey, response);
        log.info("[BDD] Re-published the same event for bookingId={}", response.getBookingId());
    }

    @Given("the following booking events are published:")
    public void theFollowingBookingEventsArePublished(DataTable table) throws Exception {
        List<Map<String, String>> rows = table.asMaps();
        for (Map<String, String> row : rows) {
            String eventType = row.get("eventType");
            BookingStatus status = resolveStatus(eventType);
            String routingKey   = resolveRoutingKey(eventType);
            String reason        = "booking.rejected".equals(eventType) ? "Time slot is already booked" : null;

            BookingResponse response = BookingResponse.builder()
                    .bookingId(row.get("bookingId"))
                    .classroomId(row.get("classroomId"))
                    .requestedBy(row.get("requestedBy"))
                    .status(status)
                    .message(status == BookingStatus.CONFIRMED
                            ? "Booking " + row.get("bookingId") + " is CONFIRMED"
                            : "Booking rejected – " + reason)
                    .date(LocalDate.now().plusDays(1))
                    .timeSlot(new TimeSlot(LocalTime.of(9, 0), LocalTime.of(10, 0)))
                    .timestamp(LocalDateTime.now())
                    .build();

            rabbitTemplate.convertAndSend(RabbitMQConstants.EXCHANGE, routingKey, response);
            log.info("[BDD] Published event: {} for {}", eventType, row.get("bookingId"));
        }
    }

    // ── When ──────────────────────────────────────────────────────────────────

    @When("the notification service processes the event")
    public void theNotificationServiceProcessesTheEvent() {
        // The notification service is continuously listening; just allow settling time
        await().atMost(10, SECONDS).pollInterval(500, MILLISECONDS)
                .until(() -> {
                    try {
                        ResponseEntity<Map> summary = restTemplate.getForEntity(
                                notificationUrl + "/test/notifications/summary", Map.class);
                        Map<?, ?> body = summary.getBody();
                        return body != null && ((Number) body.get("total")).intValue() >= 1;
                    } catch (Exception e) { return false; }
                });
        log.info("[BDD] Notification service processed event");
    }

    @When("the notification service processes all events")
    public void theNotificationServiceProcessesAllEvents() {
        theNotificationServiceProcessesTheEvent();
    }

    @When("the notification service processes both events")
    public void theNotificationServiceProcessesBothEvents() {
        // For idempotency: allow time for both events, then count should still be 1
        try { Thread.sleep(3000); } catch (InterruptedException ignored) {}
    }

    // ── Then ──────────────────────────────────────────────────────────────────

    @Then("the notification message should contain {string}")
    public void theNotificationMessageShouldContain(String expected) {
        String bookingId = ctx.getLastPublishedEventJson() != null
                ? parseBookingIdFromJson(ctx.getLastPublishedEventJson()) : null;

        await().atMost(10, SECONDS).pollInterval(500, MILLISECONDS)
                .until(() -> {
                    try {
                        String url = bookingId != null
                                ? notificationUrl + "/test/notifications/by-booking?bookingId=" + bookingId
                                : notificationUrl + "/test/notifications";
                        ResponseEntity<List> resp = restTemplate.getForEntity(url, List.class);
                        List<?> records = resp.getBody();
                        if (records == null) return false;
                        return records.stream().anyMatch(r -> {
                            String msg = (String) ((Map<?, ?>) r).get("message");
                            return msg != null && msg.contains(expected);
                        });
                    } catch (Exception e) { return false; }
                });

        log.info("[BDD] Notification message contains '{}'", expected);
    }

    @Then("{string} notifications should have been sent")
    public void nNotificationsShouldHaveBeenSent(String expectedCount) {
        int expected = Integer.parseInt(expectedCount);
        await().atMost(15, SECONDS).pollInterval(1, SECONDS)
                .until(() -> {
                    try {
                        ResponseEntity<Map> resp = restTemplate.getForEntity(
                                notificationUrl + "/test/notifications/summary", Map.class);
                        Map<?, ?> body = resp.getBody();
                        return body != null && ((Number) body.get("total")).intValue() >= expected;
                    } catch (Exception e) { return false; }
                });
        log.info("[BDD] {} notification(s) verified", expected);
    }

    @Then("only {string} notification should have been sent for {string}")
    public void onlyNNotificationShouldHaveBeenSentFor(String expectedCount, String bookingId) {
        try { Thread.sleep(3000); } catch (InterruptedException ignored) {}
        int expected = Integer.parseInt(expectedCount);
        ResponseEntity<List> resp = restTemplate.getForEntity(
                notificationUrl + "/test/notifications/by-booking?bookingId={id}",
                List.class, bookingId);
        List<?> records = resp.getBody();
        assertThat(records).as("Notifications for bookingId " + bookingId).hasSize(expected);
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private BookingResponse buildResponseFromMap(Map<String, String> row, BookingStatus status) {
        String reason = row.getOrDefault("reason", null);
        String start  = row.getOrDefault("startTime", "09:00");
        String end    = row.getOrDefault("endTime",   "10:00");
        String date   = row.getOrDefault("date", LocalDate.now().plusDays(1).toString());

        return BookingResponse.builder()
                .bookingId(row.get("bookingId"))
                .classroomId(row.get("classroomId"))
                .requestedBy(row.get("requestedBy"))
                .status(status)
                .date(LocalDate.parse(date))
                .timeSlot(new TimeSlot(LocalTime.parse(start), LocalTime.parse(end)))
                .message(status == BookingStatus.CONFIRMED
                        ? "Your booking " + row.get("bookingId") + " for " + row.get("classroomId")
                          + " on " + date + " is CONFIRMED"
                        : "Your booking request for " + row.get("classroomId")
                          + " on " + date + " has been REJECTED – "
                          + (reason != null ? reason : "Time slot is already booked"))
                .timestamp(LocalDateTime.now())
                .build();
    }

    private BookingStatus resolveStatus(String eventType) {
        return "booking.confirmed".equals(eventType) ? BookingStatus.CONFIRMED : BookingStatus.REJECTED;
    }

    private String resolveQueue(String eventType) {
        return "booking.confirmed".equals(eventType)
                ? RabbitMQConstants.QUEUE_BOOKING_CONFIRMED
                : RabbitMQConstants.QUEUE_BOOKING_REJECTED;
    }

    private String resolveRoutingKey(String eventType) {
        return "booking.confirmed".equals(eventType)
                ? RabbitMQConstants.ROUTING_KEY_CONFIRMED
                : RabbitMQConstants.ROUTING_KEY_REJECTED;
    }

    private String parseBookingIdFromJson(String json) {
        try {
            return objectMapper.readTree(json).path("bookingId").asText(null);
        } catch (Exception e) { return null; }
    }
}

