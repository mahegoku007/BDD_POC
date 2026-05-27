package com.classroom.gateway;

import com.classroom.common.dto.BookingRequest;
import com.classroom.common.dto.TimeSlot;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.apache.camel.CamelContext;
import org.apache.camel.EndpointInject;
import org.apache.camel.Exchange;
import org.apache.camel.Produce;
import org.apache.camel.ProducerTemplate;
import org.apache.camel.builder.AdviceWith;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.test.spring.junit5.CamelSpringBootTest;
import org.apache.camel.test.spring.junit5.UseAdviceWith;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.time.LocalDate;
import java.time.LocalTime;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for {@link BookingRoute} using Camel's {@code AdviceWith}
 * to replace the real HTTP downstream call with a {@code mock:} endpoint.
 *
 * <p>No live HTTP server or RabbitMQ broker is required.
 */
@CamelSpringBootTest
@SpringBootTest
@ActiveProfiles("test")
@UseAdviceWith   // prevents CamelContext auto-start so AdviceWith can modify routes first
@DisplayName("BookingRoute – Camel route unit tests")
class BookingRouteTest {

    @Autowired
    private CamelContext camelContext;

    @Produce("direct:validateBooking")
    private ProducerTemplate validateTemplate;

    @Produce("direct:submitBooking")
    private ProducerTemplate submitTemplate;

    @EndpointInject("mock:bookingService")
    private MockEndpoint mockBookingService;

    private final ObjectMapper mapper = new ObjectMapper()
            .registerModule(new JavaTimeModule())
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

    @BeforeEach
    void setUp() throws Exception {
        // Replace the real HTTP call in submit-booking-route with a mock endpoint
        AdviceWith.adviceWith(camelContext, "submit-booking-route", advice ->
                advice.weaveByToUri("http*://*/bookings*")
                      .replace()
                      .to("mock:bookingService"));

        if (!camelContext.isStarted()) {
            camelContext.start();
        }
        mockBookingService.reset();
    }

    // ─────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("valid request reaches the booking service mock exactly once")
    void validRequestForwardedToBookingService() throws Exception {
        mockBookingService.expectedMessageCount(1);
        mockBookingService.whenAnyExchangeReceived(exchange -> {
            exchange.getMessage().setHeader(Exchange.HTTP_RESPONSE_CODE, 202);
            exchange.getMessage().setBody(mapper.writeValueAsString(validRequest("BK-001")));
        });

        submitTemplate.sendBody(mapper.writeValueAsString(validRequest(null)));

        mockBookingService.assertIsSatisfied();
    }

    @Test
    @DisplayName("202 response from booking service is propagated back to caller")
    void accepts202FromBookingService() throws Exception {
        BookingRequest response = validRequest("BK-test01");
        mockBookingService.whenAnyExchangeReceived(exchange -> {
            exchange.getMessage().setHeader(Exchange.HTTP_RESPONSE_CODE, 202);
            exchange.getMessage().setBody(mapper.writeValueAsString(response));
        });

        Exchange result = submitTemplate.send(exchange -> {
            exchange.getMessage().setBody(mapper.writeValueAsString(validRequest(null)));
            exchange.getMessage().setHeader(Exchange.CONTENT_TYPE, "application/json");
        });

        assertThat(result.getMessage().getHeader(Exchange.HTTP_RESPONSE_CODE, Integer.class))
                .isEqualTo(202);
    }

    @Test
    @DisplayName("validateBooking route throws IllegalArgumentException for missing classroomId")
    void validateRejectsMissingClassroomId() {
        BookingRequest noClassroom = validRequest(null);
        noClassroom.setClassroomId(null);

        Exchange result = validateTemplate.send(exchange ->
                exchange.getMessage().setBody(noClassroom));

        assertThat(result.getException())
                .isNotNull()
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("classroomId");
    }

    @Test
    @DisplayName("validateBooking route throws IllegalArgumentException for missing requestedBy")
    void validateRejectsMissingRequestedBy() {
        BookingRequest noRequester = validRequest(null);
        noRequester.setRequestedBy(null);

        Exchange result = validateTemplate.send(exchange ->
                exchange.getMessage().setBody(noRequester));

        assertThat(result.getException())
                .isNotNull()
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("requestedBy");
    }

    @Test
    @DisplayName("validateBooking route passes a complete, valid request through")
    void validatePassesValidRequest() throws Exception {
        mockBookingService.whenAnyExchangeReceived(exchange -> {
            exchange.getMessage().setHeader(Exchange.HTTP_RESPONSE_CODE, 202);
            exchange.getMessage().setBody(mapper.writeValueAsString(validRequest("BK-x")));
        });

        Exchange result = validateTemplate.send(exchange ->
                exchange.getMessage().setBody(validRequest(null)));

        // No exception means validation passed and route continued
        assertThat(result.getException()).isNull();
    }

    // ── Helper ─────────────────────────────────────────────────────────────

    private BookingRequest validRequest(String bookingId) {
        return BookingRequest.builder()
                .bookingId(bookingId)
                .classroomId("CR-101")
                .date(LocalDate.now().plusDays(1))
                .timeSlot(new TimeSlot(LocalTime.of(9, 0), LocalTime.of(10, 0)))
                .requestedBy("alice@example.com")
                .build();
    }
}

