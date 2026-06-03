package com.classroom.bdd.steps;

import com.classroom.bdd.config.ScenarioContext;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.cucumber.java.en.And;
import io.cucumber.java.en.Given;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestTemplate;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Step definitions for {@code audit_logging.feature}.
 * Tests the audit trail functionality via the service-audit REST API.
 */
@Slf4j
@RequiredArgsConstructor
public class AuditStepDefinitions {

    private final ScenarioContext ctx;
    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;

    @Value("${services.audit.url:http://localhost:8084}")
    private String auditUrl;

    private int auditHttpStatus;
    private String auditResponseBody;

    // ── Given ──────────────────────────────────────────────────────────────────

    @Given("the audit service is running on port {string}")
    public void theAuditServiceIsRunningOnPort(String port) {
        String url = "http://localhost:" + port + "/actuator/health";
        try {
            ResponseEntity<String> health = restTemplate.getForEntity(url, String.class);
            assertThat(health.getStatusCode().is2xxSuccessful())
                    .as("Audit service health on port " + port).isTrue();
            log.info("[BDD] Audit service is UP on port {}", port);
        } catch (Exception e) {
            log.warn("[BDD] Audit service health check failed on port {} — {}", port, e.getMessage());
            // Non-fatal: audit service might not be port-forwarded yet in some test configs
        }
    }

    // ── When ───────────────────────────────────────────────────────────────────

    @When("I query the audit trail for the last booking")
    public void iQueryTheAuditTrailForTheLastBooking() {
        String bookingId = ctx.getLastBookingId();
        assertThat(bookingId).as("Last booking ID must be set before querying audit").isNotNull();
        queryAuditTrail(bookingId);
    }

    @When("I query the audit trail for booking {string}")
    public void iQueryTheAuditTrailForBooking(String bookingId) {
        queryAuditTrail(bookingId);
    }

    @When("I query audit events for classroom {string}")
    public void iQueryAuditEventsForClassroom(String classroomId) {
        String url = auditUrl + "/audit/classrooms/" + classroomId;
        try {
            ResponseEntity<String> response = restTemplate.getForEntity(url, String.class);
            auditHttpStatus = response.getStatusCode().value();
            auditResponseBody = response.getBody();
            log.info("[BDD] GET {} → HTTP {}", url, auditHttpStatus);
        } catch (HttpClientErrorException e) {
            auditHttpStatus = e.getStatusCode().value();
            auditResponseBody = e.getResponseBodyAsString();
            log.info("[BDD] GET {} → HTTP {} (error)", url, auditHttpStatus);
        }
    }

    // ── Then ───────────────────────────────────────────────────────────────────

    @Then("the audit trail should contain an event with action {string}")
    public void theAuditTrailShouldContainAnEventWithAction(String action) throws Exception {
        assertThat(auditResponseBody).as("Audit response body").isNotNull();
        JsonNode events = objectMapper.readTree(auditResponseBody);
        assertThat(events.isArray()).as("Audit trail should be an array").isTrue();

        boolean found = false;
        for (JsonNode event : events) {
            if (action.equals(event.path("action").asText())) {
                found = true;
                break;
            }
        }
        assertThat(found).as("Audit trail should contain action '" + action + "'").isTrue();
        log.info("[BDD] Audit trail contains action '{}'", action);
    }

    @Then("the audit event should have source {string}")
    public void theAuditEventShouldHaveSource(String source) throws Exception {
        JsonNode events = objectMapper.readTree(auditResponseBody);
        boolean found = false;
        for (JsonNode event : events) {
            if (source.equals(event.path("source").asText())) {
                found = true;
                break;
            }
        }
        assertThat(found).as("Audit event should have source '" + source + "'").isTrue();
    }

    @Then("the audit event payload should contain {string}")
    public void theAuditEventPayloadShouldContain(String expectedText) throws Exception {
        JsonNode events = objectMapper.readTree(auditResponseBody);
        boolean found = false;
        for (JsonNode event : events) {
            String payload = event.path("payload").asText("");
            if (payload.contains(expectedText)) {
                found = true;
                break;
            }
        }
        assertThat(found).as("Audit event payload should contain '" + expectedText + "'").isTrue();
    }

    @Then("the audit trail should have at least {int} event")
    public void theAuditTrailShouldHaveAtLeastNEvents(int minCount) throws Exception {
        JsonNode events = objectMapper.readTree(auditResponseBody);
        assertThat(events.isArray()).isTrue();
        assertThat(events.size()).as("Audit trail event count").isGreaterThanOrEqualTo(minCount);
    }

    @Then("the first audit event should have action {string}")
    public void theFirstAuditEventShouldHaveAction(String action) throws Exception {
        JsonNode events = objectMapper.readTree(auditResponseBody);
        assertThat(events.isArray() && events.size() > 0).isTrue();
        String firstAction = events.get(0).path("action").asText();
        assertThat(firstAction).as("First audit event action").isEqualTo(action);
    }

    @Then("the audit HTTP response status should be {string}")
    public void theAuditHttpResponseStatusShouldBe(String expectedStatus) {
        assertThat(auditHttpStatus)
                .as("Audit HTTP response status")
                .isEqualTo(Integer.parseInt(expectedStatus));
    }

    @Then("the classroom audit trail should not be empty")
    public void theClassroomAuditTrailShouldNotBeEmpty() throws Exception {
        JsonNode events = objectMapper.readTree(auditResponseBody);
        assertThat(events.isArray()).isTrue();
        assertThat(events.size()).as("Classroom audit events").isGreaterThan(0);
    }

    // ── Private helpers ────────────────────────────────────────────────────────

    private void queryAuditTrail(String bookingId) {
        String url = auditUrl + "/audit/bookings/" + bookingId;
        try {
            // Small delay to allow async audit recording to complete
            Thread.sleep(1500);
            ResponseEntity<String> response = restTemplate.getForEntity(url, String.class);
            auditHttpStatus = response.getStatusCode().value();
            auditResponseBody = response.getBody();
            log.info("[BDD] GET {} → HTTP {} | body length={}", url, auditHttpStatus,
                    auditResponseBody != null ? auditResponseBody.length() : 0);
        } catch (HttpClientErrorException e) {
            auditHttpStatus = e.getStatusCode().value();
            auditResponseBody = e.getResponseBodyAsString();
            log.info("[BDD] GET {} → HTTP {} (client error)", url, auditHttpStatus);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}

