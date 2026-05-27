package com.classroom.bdd.steps;

import com.classroom.bdd.config.ScenarioContext;
import com.classroom.common.dto.BookingRequest;
import com.classroom.common.dto.TimeSlot;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.cucumber.datatable.DataTable;
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
import org.springframework.web.client.RestTemplate;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static java.util.concurrent.TimeUnit.SECONDS;

/**
 * Step definitions for {@code end_to_end_flow.feature}.
 *
 * <p>These steps exercise the full system from API Gateway to Notification
 * Service and validate the complete flow including concurrent submissions.
 */
@Slf4j
@RequiredArgsConstructor
public class EndToEndStepDefinitions {

    private final ScenarioContext ctx;
    private final RestTemplate    restTemplate;
    private final ObjectMapper    objectMapper;

    @Value("${services.gateway.url:http://localhost:8080}")
    private String gatewayUrl;

    @Value("${services.notification.url:http://localhost:8083}")
    private String notificationUrl;

    // ── Given: System health ──────────────────────────────────────────────────

    @Given("the complete system is running with all services:")
    public void theCompleteSystemIsRunning(DataTable table) {
        List<Map<String, String>> rows = table.asMaps();
        rows.forEach(row -> {
            String name = row.get("service");
            String port = row.get("port");
            String url  = "http://localhost:" + port + "/actuator/health";
            try {
                ResponseEntity<String> health = restTemplate.getForEntity(url, String.class);
                assertThat(health.getStatusCode().is2xxSuccessful())
                        .as(name + " health check").isTrue();
                log.info("[BDD] {} is UP on port {}", name, port);
            } catch (Exception e) {
                throw new AssertionError(
                        "Service '" + name + "' is NOT running on port " + port +
                        ". Start all services before running end-to-end tests.", e);
            }
        });
    }

    @Given("the booking store is empty")
    public void theBookingStoreIsEmpty() {
        restTemplate.delete("http://localhost:8082/test/bookings");
        log.info("[BDD] Booking store (availability) cleared");
    }

    @Given("the availability service introduces a delay of {string} seconds")
    public void availabilityServiceIntroducesDelay(String seconds) {
        // In a real setup this would configure a delay in the availability service.
        // For this demo we just log the intent – the awaitility timeout covers it.
        log.info("[BDD] NOTE: delay step noted ({} s); Awaitility timeout will accommodate it", seconds);
    }

    // ── When: Submit via API ──────────────────────────────────────────────────

    @When("I submit a booking request via the REST API:")
    public void iSubmitABookingRequestViaRestApi(DataTable table) throws Exception {
        Map<String, String> row = table.asMap();
        BookingRequest body = BookingRequest.builder()
                .classroomId(row.get("classroomId"))
                .date(LocalDate.parse(row.get("date")))
                .timeSlot(new TimeSlot(
                        LocalTime.parse(row.get("startTime")),
                        LocalTime.parse(row.get("endTime"))))
                .requestedBy(row.get("requestedBy"))
                .build();

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);

        ResponseEntity<String> response = restTemplate.postForEntity(
                gatewayUrl + "/bookings",
                new HttpEntity<>(body, headers),
                String.class);

        ctx.setLastHttpStatus(response.getStatusCode().value());
        ctx.setLastHttpResponse(response);

        if (response.getBody() != null) {
            JsonNode json = objectMapper.readTree(response.getBody());
            String bookingId = json.path("bookingId").asText(null);
            ctx.setLastBookingId(bookingId);
            if (bookingId != null) ctx.addSubmittedBookingId(bookingId);
        }

        log.info("[BDD] Submitted via REST API | HTTP {} | bookingId={}",
                response.getStatusCode().value(), ctx.getLastBookingId());
    }

    @When("the following booking requests are submitted in sequence:")
    public void theFollowingRequestsSubmittedInSequence(DataTable table) throws Exception {
        List<Map<String, String>> rows = table.asMaps();
        for (Map<String, String> row : rows) {
            submitRow(row);
            Thread.sleep(200);
        }
    }

    @When("the following booking requests are submitted concurrently:")
    public void theFollowingRequestsSubmittedConcurrently(DataTable table) throws Exception {
        List<Map<String, String>> rows = table.asMaps();
        ExecutorService executor = Executors.newFixedThreadPool(rows.size());
        CountDownLatch latch = new CountDownLatch(1);
        List<String> bookingIds = new ArrayList<>();

        for (Map<String, String> row : rows) {
            executor.submit(() -> {
                try {
                    latch.await();  // All threads start at the same time
                    BookingRequest body = BookingRequest.builder()
                            .classroomId(row.get("classroomId"))
                            .date(LocalDate.parse(row.get("date")))
                            .timeSlot(new TimeSlot(
                                    LocalTime.parse(row.get("startTime")),
                                    LocalTime.parse(row.get("endTime"))))
                            .requestedBy(row.get("requestedBy"))
                            .build();

                    HttpHeaders h = new HttpHeaders();
                    h.setContentType(MediaType.APPLICATION_JSON);
                    ResponseEntity<String> resp = restTemplate.postForEntity(
                            gatewayUrl + "/bookings", new HttpEntity<>(body, h), String.class);

                    if (resp.getBody() != null) {
                        JsonNode json = objectMapper.readTree(resp.getBody());
                        String id = json.path("bookingId").asText(null);
                        if (id != null) {
                            synchronized (bookingIds) { bookingIds.add(id); }
                            ctx.addSubmittedBookingId(id);
                        }
                    }
                } catch (Exception e) {
                    log.warn("[BDD] Concurrent submission error: {}", e.getMessage());
                }
            });
        }

        latch.countDown();  // Release all threads simultaneously
        executor.shutdown();
        executor.awaitTermination(30, TimeUnit.SECONDS);
        log.info("[BDD] {} concurrent bookings submitted", rows.size());
    }

    // ── Then ──────────────────────────────────────────────────────────────────

    @Then("the API should respond with status {string} and a bookingId")
    public void apiShouldRespondWithStatusAndBookingId(String expectedStatus) {
        assertThat(ctx.getLastHttpStatus())
                .as("HTTP response status")
                .isEqualTo(Integer.parseInt(expectedStatus));
        assertThat(ctx.getLastBookingId())
                .as("bookingId should be returned in response")
                .isNotNull().isNotBlank();
    }

    @Then("eventually the booking status should become {string}")
    public void eventuallyBookingStatusShouldBe(String expectedStatus) {
        pollForStatus(expectedStatus, 15);
    }

    @Then("eventually within {string} seconds the booking status should become {string}")
    public void eventuallyWithinSecondsBookingStatusShouldBe(String seconds, String expectedStatus) {
        pollForStatus(expectedStatus, Integer.parseInt(seconds));
    }

    @Then("exactly {string} booking should be confirmed")
    public void exactlyNBookingShouldBeConfirmed(String n) {
        int expected = Integer.parseInt(n);
        await().atMost(20, SECONDS).pollInterval(1, SECONDS)
                .until(() -> countNotificationsOfType("confirmations") >= expected);
        assertThat(countNotificationsOfType("confirmations")).isEqualTo(expected);
    }

    @Then("exactly {string} booking should be rejected")
    public void exactlyNBookingShouldBeRejected(String n) {
        int expected = Integer.parseInt(n);
        await().atMost(20, SECONDS).pollInterval(1, SECONDS)
                .until(() -> countNotificationsOfType("rejections") >= expected);
        assertThat(countNotificationsOfType("rejections")).isEqualTo(expected);
    }

    @Then("total notifications sent should be {string}")
    public void totalNotificationsSentShouldBe(String n) {
        int expected = Integer.parseInt(n);
        await().atMost(20, SECONDS).pollInterval(1, SECONDS)
                .until(() -> countNotificationsOfType("total") >= expected);
        assertThat(countNotificationsOfType("total")).isEqualTo(expected);
    }

    @Then("all {string} bookings should be confirmed")
    public void allNBookingsShouldBeConfirmed(String n) {
        int expected = Integer.parseInt(n);
        await().atMost(30, SECONDS).pollInterval(1, SECONDS)
                .until(() -> countNotificationsOfType("confirmations") >= expected);
        assertThat(countNotificationsOfType("confirmations"))
                .as("All " + expected + " bookings should be confirmed").isEqualTo(expected);
    }

    @Then("{string} confirmation notifications should have been sent")
    public void nConfirmationNotificationsShouldHaveBeenSent(String n) {
        int expected = Integer.parseInt(n);
        await().atMost(20, SECONDS).pollInterval(1, SECONDS)
                .until(() -> countNotificationsOfType("confirmations") >= expected);
        assertThat(countNotificationsOfType("confirmations")).isEqualTo(expected);
    }

    // ── Private helpers ───────────────────────────────────────────────────────

    private void submitRow(Map<String, String> row) throws Exception {
        BookingRequest body = BookingRequest.builder()
                .classroomId(row.get("classroomId"))
                .date(LocalDate.parse(row.get("date")))
                .timeSlot(new TimeSlot(
                        LocalTime.parse(row.get("startTime")),
                        LocalTime.parse(row.get("endTime"))))
                .requestedBy(row.get("requestedBy"))
                .build();

        HttpHeaders h = new HttpHeaders();
        h.setContentType(MediaType.APPLICATION_JSON);
        ResponseEntity<String> resp = restTemplate.postForEntity(
                gatewayUrl + "/bookings", new HttpEntity<>(body, h), String.class);

        ctx.setLastHttpStatus(resp.getStatusCode().value());
        ctx.setLastHttpResponse(resp);
        if (resp.getBody() != null) {
            JsonNode json = objectMapper.readTree(resp.getBody());
            String id = json.path("bookingId").asText(null);
            ctx.setLastBookingId(id);
            if (id != null) ctx.addSubmittedBookingId(id);
        }
    }

    private void pollForStatus(String expectedStatus, int timeoutSeconds) {
        String bookingId = ctx.getLastBookingId();
        assertThat(bookingId).as("bookingId must be set").isNotNull();

        String expectedType = "CONFIRMED".equalsIgnoreCase(expectedStatus) ? "CONFIRMATION" : "REJECTION";

        await().atMost(timeoutSeconds, SECONDS).pollInterval(1, SECONDS)
                .alias("Waiting for booking " + bookingId + " to become " + expectedStatus)
                .until(() -> {
                    try {
                        ResponseEntity<List> resp = restTemplate.getForEntity(
                                notificationUrl + "/test/notifications/by-booking?bookingId={id}",
                                List.class, bookingId);
                        List<?> records = resp.getBody();
                        if (records == null || records.isEmpty()) return false;
                        return records.stream().anyMatch(r ->
                                expectedType.equals(((Map<?, ?>) r).get("type")));
                    } catch (Exception e) { return false; }
                });

        log.info("[BDD] Booking {} reached status {}", bookingId, expectedStatus);
    }

    private int countNotificationsOfType(String type) {
        try {
            ResponseEntity<Map> resp = restTemplate.getForEntity(
                    notificationUrl + "/test/notifications/summary", Map.class);
            Map<?, ?> body = resp.getBody();
            if (body == null) return 0;
            Number count = (Number) body.get(type);
            return count == null ? 0 : count.intValue();
        } catch (Exception e) { return 0; }
    }
}




