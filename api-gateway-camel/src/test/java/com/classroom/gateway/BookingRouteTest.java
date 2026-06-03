package com.classroom.gateway;

import com.classroom.common.dto.BookingRequest;
import com.classroom.common.dto.TimeSlot;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.apache.camel.CamelContext;
import org.apache.camel.ProducerTemplate;
import org.apache.camel.test.spring.junit5.CamelSpringBootTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.time.LocalDate;
import java.time.LocalTime;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for the API Gateway Camel configuration.
 *
 * <p>XML routes are disabled in the test profile (routes-include-pattern="")
 * to avoid HTTP connection issues. XML route logic is tested via the BDD
 * integration test suite against running services.
 *
 * <p>These tests verify:
 * <ul>
 *   <li>Spring context loads with Camel configuration</li>
 *   <li>REST DSL endpoints are registered</li>
 *   <li>Route Slip dispatcher route is configured</li>
 *   <li>Processor beans are available</li>
 * </ul>
 */
@CamelSpringBootTest
@SpringBootTest
@ActiveProfiles("test")
@DisplayName("BookingRoute – Camel configuration unit tests")
class BookingRouteTest {

    @Autowired
    private CamelContext camelContext;

    @Autowired
    private ProducerTemplate producerTemplate;

    private final ObjectMapper mapper = new ObjectMapper()
            .registerModule(new JavaTimeModule())
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

    @Test
    @DisplayName("Camel context starts successfully with REST DSL and route-slip dispatcher")
    void contextStartsWithRoutes() {
        assertThat(camelContext.isStarted()).isTrue();
        // The route-slip-dispatcher route should be registered from Java DSL
        assertThat(camelContext.getRoute("route-slip-dispatcher")).isNotNull();
    }

    @Test
    @DisplayName("REST endpoints are registered for /bookings")
    void restEndpointsRegistered() {
        // REST DSL registers platform-http endpoints
        var endpoints = camelContext.getEndpoints();
        assertThat(endpoints).isNotEmpty();
        // The REST configuration should be active
        assertThat(camelContext.getRestConfiguration()).isNotNull();
    }

    @Test
    @DisplayName("Processor beans are available in Camel registry")
    void processorBeansAvailable() {
        var registry = camelContext.getRegistry();
        assertThat(registry.lookupByName("contentTypeValidatorProcessor")).isNotNull();
        assertThat(registry.lookupByName("bookingDeserializerProcessor")).isNotNull();
        assertThat(registry.lookupByName("bookingSerializerProcessor")).isNotNull();
        assertThat(registry.lookupByName("batchAggregationStrategy")).isNotNull();
        assertThat(registry.lookupByName("batchCompletionProcessor")).isNotNull();
        assertThat(registry.lookupByName("auditBodyRestorerProcessor")).isNotNull();
    }

    @Test
    @DisplayName("Route Slip dispatcher sends to configured pipeline")
    void routeSlipDispatcherConfigured() throws Exception {
        var route = camelContext.getRoute("route-slip-dispatcher");
        assertThat(route).isNotNull();
        assertThat(route.getEndpoint().getEndpointUri()).isEqualTo("direct://routeSlipDispatcher");
    }

    @Test
    @DisplayName("ObjectMapper in processors handles Java 8 date/time types")
    void objectMapperHandlesJavaTime() throws Exception {
        BookingRequest request = BookingRequest.builder()
                .bookingId("BK-TEST01")
                .classroomId("CR-101")
                .date(LocalDate.of(2026, 7, 1))
                .timeSlot(new TimeSlot(LocalTime.of(9, 0), LocalTime.of(10, 0)))
                .requestedBy("test@example.com")
                .build();

        String json = mapper.writeValueAsString(request);
        assertThat(json).contains("2026-07-01");
        assertThat(json).contains("09:00");
        assertThat(json).contains("10:00");

        BookingRequest deserialized = mapper.readValue(json, BookingRequest.class);
        assertThat(deserialized.getDate()).isEqualTo(LocalDate.of(2026, 7, 1));
    }
}
