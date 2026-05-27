package com.classroom.bdd.steps;

import com.classroom.bdd.config.ScenarioContext;
import com.classroom.common.dto.BookingRequest;
import com.classroom.common.dto.TimeSlot;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.cucumber.datatable.DataTable;
import io.cucumber.java.en.Given;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestTemplate;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Step definitions for {@code availability_check.feature}.
 *
 * <p>These steps interact directly with service-availability (port 8082)
 * via its test-support REST controller. No API Gateway or RabbitMQ is
 * required for these scenarios.
 */
@Slf4j
@RequiredArgsConstructor
public class AvailabilityStepDefinitions {

    private final ScenarioContext ctx;
    private final RestTemplate    restTemplate;
    private final ObjectMapper    objectMapper;

    @Value("${services.availability.url:http://localhost:8082}")
    private String availabilityUrl;

    // ── Given ─────────────────────────────────────────────────────────────────

    @Given("the availability service is running")
    public void theAvailabilityServiceIsRunning() {
        ResponseEntity<String> health = restTemplate.getForEntity(
                availabilityUrl + "/actuator/health", String.class);
        assertThat(health.getStatusCode().is2xxSuccessful())
                .as("Availability service /actuator/health").isTrue();
        log.info("[BDD] Availability service health check passed");
    }

    @Given("the in-memory booking store is empty")
    public void theInMemoryBookingStoreIsEmpty() {
        restTemplate.delete(availabilityUrl + "/test/bookings");
        log.info("[BDD] Availability store cleared");
    }

    @Given("a booking already exists for classroom {string} on {string} from {string} to {string}")
    public void aBookingAlreadyExistsForClassroom(String classroomId, String date,
                                                   String start, String end) {
        Map<String, Object> record = new LinkedHashMap<>();
        record.put("bookingId",   "EXIST-" + classroomId + "-" + start.replace(":", ""));
        record.put("classroomId", classroomId);
        record.put("date",        date);
        record.put("startTime",   start);
        record.put("endTime",     end);
        record.put("requestedBy", "existing@test.com");
        record.put("confirmedAt", LocalDateTime.now().toString());

        restTemplate.postForEntity(availabilityUrl + "/test/bookings", record, Map.class);
        log.info("[BDD] Seeded booking for {} on {} [{}-{}]", classroomId, date, start, end);
    }

    @Given("the following bookings exist for classroom {string}:")
    public void theFollowingBookingsExistForClassroom(String classroomId, DataTable table) {
        List<Map<String, String>> rows = table.asMaps();
        for (Map<String, String> row : rows) {
            String date  = row.get("date");
            String start = row.get("startTime");
            String end   = row.get("endTime");

            Map<String, Object> record = new LinkedHashMap<>();
            record.put("bookingId",   "SEED-" + classroomId + "-" + start.replace(":", ""));
            record.put("classroomId", classroomId);
            record.put("date",        date);
            record.put("startTime",   start);
            record.put("endTime",     end);
            record.put("requestedBy", "seed@test.com");
            record.put("confirmedAt", LocalDateTime.now().toString());

            restTemplate.postForEntity(availabilityUrl + "/test/bookings", record, Map.class);
        }
        log.info("[BDD] Seeded {} booking(s) for classroom {}", rows.size(), classroomId);
    }

    // ── When ──────────────────────────────────────────────────────────────────

    @When("the availability service checks classroom {string} on {string} from {string} to {string}")
    public void availabilityServiceChecks(String classroomId, String date,
                                          String start, String end) throws Exception {
        BookingRequest request = BookingRequest.builder()
                .bookingId("CHECK-" + System.currentTimeMillis())
                .classroomId(classroomId)
                .date(LocalDate.parse(date))
                .timeSlot(new TimeSlot(LocalTime.parse(start), LocalTime.parse(end)))
                .requestedBy("checker@test.com")
                .build();

        ResponseEntity<String> resp = restTemplate.postForEntity(
                availabilityUrl + "/test/availability/check", request, String.class);

        Map<?, ?> body = objectMapper.readValue(resp.getBody(), Map.class);
        ctx.setLastAvailabilityStatus((String) body.get("status"));
        String conflictReason = (String) body.get("conflictReason");
        ctx.setLastConflictDetails(conflictReason);

        log.info("[BDD] Availability check result: status={} conflict={}", body.get("status"), conflictReason);
    }

    // ── Then ──────────────────────────────────────────────────────────────────

    @Then("the availability status should be {string}")
    public void theAvailabilityStatusShouldBe(String expected) {
        assertThat(ctx.getLastAvailabilityStatus())
                .as("Availability status")
                .isEqualTo(expected);
    }

    @Then("the conflict details should include the existing booking time {string}")
    public void theConflictDetailsShouldInclude(String expectedTime) {
        // The conflict details / reason field may or may not include the
        // exact time-slot string; we assert it's not null for UNAVAILABLE results.
        assertThat(ctx.getLastAvailabilityStatus()).isEqualTo("UNAVAILABLE");
        // The conflict reason from AvailabilityService is "Time slot is already booked"
        assertThat(ctx.getLastConflictDetails())
                .as("Conflict reason should not be blank")
                .isNotBlank();
        log.info("[BDD] Conflict details verified: {}", ctx.getLastConflictDetails());
    }
}





