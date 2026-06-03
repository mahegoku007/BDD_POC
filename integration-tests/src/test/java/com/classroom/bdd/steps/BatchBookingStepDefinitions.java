package com.classroom.bdd.steps;

import com.classroom.bdd.config.ScenarioContext;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.cucumber.java.en.Then;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.client.RestTemplate;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Step definitions for {@code batch_booking.feature}.
 * Tests the batch booking endpoint that uses Camel Split + Aggregate pattern.
 */
@Slf4j
@RequiredArgsConstructor
public class BatchBookingStepDefinitions {

    private final ScenarioContext ctx;
    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;

    @Value("${services.gateway.url:http://localhost:8080}")
    private String gatewayUrl;

    // ── Then: Batch response assertions ────────────────────────────────────────

    @Then("the response body should be a JSON array of size {int}")
    public void theResponseBodyShouldBeAJsonArrayOfSize(int expectedSize) throws Exception {
        String body = requireResponseBody();
        JsonNode json = objectMapper.readTree(body);
        assertThat(json.isArray())
                .as("Response body should be a JSON array. Body: " + body)
                .isTrue();
        assertThat(json.size())
                .as("JSON array size")
                .isEqualTo(expectedSize);
        log.info("[BDD] Response is JSON array of size {}", json.size());
    }

    @Then("each item in the batch response should contain field {string}")
    public void eachItemInTheBatchResponseShouldContainField(String field) throws Exception {
        String body = requireResponseBody();
        JsonNode json = objectMapper.readTree(body);
        assertThat(json.isArray()).isTrue();

        for (int i = 0; i < json.size(); i++) {
            JsonNode item = json.get(i);
            assertThat(item.has(field))
                    .as("Item " + i + " should contain field '" + field + "'. Item: " + item)
                    .isTrue();
        }
        log.info("[BDD] All {} items in batch response contain field '{}'", json.size(), field);
    }

    @Then("the batch response should contain at least one error item")
    public void theBatchResponseShouldContainAtLeastOneErrorItem() throws Exception {
        String body = requireResponseBody();
        JsonNode json = objectMapper.readTree(body);
        assertThat(json.isArray()).isTrue();

        boolean hasError = false;
        for (JsonNode item : json) {
            if (item.has("error")) {
                hasError = true;
                break;
            }
        }
        assertThat(hasError)
                .as("Batch response should contain at least one error item. Body: " + body)
                .isTrue();
        log.info("[BDD] Batch response contains at least one error item");
    }

    // ── Private helpers ────────────────────────────────────────────────────────

    private String requireResponseBody() {
        assertThat(ctx.getLastHttpResponse()).as("Last HTTP response must not be null").isNotNull();
        String body = ctx.getLastHttpResponse().getBody();
        assertThat(body).as("Response body must not be null").isNotNull();
        return body;
    }
}
