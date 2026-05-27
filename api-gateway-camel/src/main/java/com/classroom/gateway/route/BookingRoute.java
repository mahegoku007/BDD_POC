package com.classroom.gateway.route;

import com.classroom.common.dto.BookingRequest;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.camel.Exchange;
import org.apache.camel.LoggingLevel;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.model.rest.RestBindingMode;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.net.ConnectException;
import com.classroom.gateway.exception.UnsupportedMediaTypeException;

/**
 * Primary Apache Camel route for the API Gateway.
 *
 * <p><strong>Routes defined:</strong>
 * <ul>
 *   <li>{@code POST /bookings}          – accepts booking requests, forwards to service-booking</li>
 *   <li>{@code GET  /bookings/health}   – lightweight gateway health probe</li>
 *   <li>{@code direct:submitBooking}    – internal route: validation → logging → HTTP forward</li>
 *   <li>{@code direct:gatewayHealth}    – internal route: returns gateway status JSON</li>
 * </ul>
 *
 * <p><strong>HTTP forwarding flow:</strong>
 * <pre>
 *   Client
 *     │  POST /bookings
 *     ▼
 *   [REST DSL]  ──► direct:validateBooking  ──► direct:submitBooking
 *                                                       │
 *                                                       │  HTTP POST
 *                                                       ▼
 *                                              service-booking :8081/bookings
 *                                                       │
 *                                                       │  202 Accepted + body
 *                                                       ▼
 *                                              Client sees 202 + BookingRequest JSON
 * </pre>
 *
 * <p><strong>Error handling:</strong>
 * <ul>
 *   <li>{@link ConnectException} → 503 Service Unavailable (booking service down)</li>
 *   <li>Any other {@link Exception} → 500 Internal Server Error</li>
 * </ul>
 */
@Component
public class BookingRoute extends RouteBuilder {

    /** Base URL for service-booking. Overridden in tests via application-test.yml. */
    @Value("${services.booking.url:http://localhost:8081}")
    private String bookingServiceUrl;

    /** Spring-managed ObjectMapper — has JavaTimeModule registered via JacksonConfig. */
    @Autowired
    private ObjectMapper objectMapper;

    @Override
    public void configure() {

        // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
        // 1.  GLOBAL ERROR HANDLING
        // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

        // Unsupported Content-Type (not application/json) → 415
        onException(UnsupportedMediaTypeException.class)
                .handled(true)
                .log(LoggingLevel.WARN,
                        "[GATEWAY] Unsupported Media Type: ${exception.message}")
                .setHeader(Exchange.HTTP_RESPONSE_CODE, constant(415))
                .setHeader(Exchange.CONTENT_TYPE, constant("application/json"))
                .setBody(simple(
                        "{\"error\":\"Unsupported Media Type\",\"message\":\"${exception.message}\"}"));

        // service-booking is down or network unreachable
        onException(ConnectException.class)
                .handled(true)
                .log(LoggingLevel.ERROR,
                        "[GATEWAY] Cannot reach booking service: ${exception.message}")
                .setHeader(Exchange.HTTP_RESPONSE_CODE, constant(503))
                .setHeader(Exchange.CONTENT_TYPE, constant("application/json"))
                .setBody(constant(
                        "{\"error\":\"Booking service is currently unavailable. Please try again later.\"}"));

        // Validation failure raised explicitly by the validation route
        onException(IllegalArgumentException.class)
                .handled(true)
                .log(LoggingLevel.WARN,
                        "[GATEWAY] Request validation failed: ${exception.message}")
                .setHeader(Exchange.HTTP_RESPONSE_CODE, constant(400))
                .setHeader(Exchange.CONTENT_TYPE, constant("application/json"))
                .setBody(simple(
                        "{\"error\":\"Bad Request\",\"message\":\"${exception.message}\"}"));

        // Catch-all
        onException(Exception.class)
                .handled(true)
                .log(LoggingLevel.ERROR,
                        "[GATEWAY] Unexpected error: ${exception.message}")
                .setHeader(Exchange.HTTP_RESPONSE_CODE, constant(500))
                .setHeader(Exchange.CONTENT_TYPE, constant("application/json"))
                .setBody(constant(
                        "{\"error\":\"Internal gateway error. Please try again later.\"}"));

        // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
        // 2.  REST CONFIGURATION
        //     platform-http shares Spring Boot's embedded Tomcat on port 8080.
        //     Binding is OFF: we parse / serialise JSON manually via ObjectMapper
        //     to avoid Camel's own Jackson instance (which lacks JavaTimeModule).
        // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

        restConfiguration()
                .component("platform-http")
                .bindingMode(RestBindingMode.off)   // manual JSON handling
                .enableCORS(true)
                .corsHeaderProperty("Access-Control-Allow-Origin", "*")
                .corsHeaderProperty("Access-Control-Allow-Headers",
                        "Content-Type, Accept, Authorization");

        // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
        // 3.  REST ENDPOINTS
        // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

        rest("/bookings")
                .description("Classroom Booking API")

                // ── POST /bookings ──────────────────────────────────────────
                .post()
                    .description("Submit a new classroom booking request")
                    .consumes("application/json")
                    .produces("application/json")
                    .to("direct:validateBooking");   // body is raw JSON String

        rest("/bookings")
                // ── GET /bookings ───────────────────────────────────────────
                .get()
                    .description("Gateway info endpoint")
                    .produces("application/json")
                    .to("direct:gatewayInfo");

        // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
        // 4.  INTERNAL ROUTE: Validate Booking Request
        //     Parse JSON → BookingRequest, run field guards, forward.
        // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

        from("direct:validateBooking")
                .routeId("validate-booking-route")
                .log("[GATEWAY] Validating incoming booking request")

                // ── Content-Type guard: reject non-JSON requests with 415 ────────────
                // When bindingMode is OFF, Camel does not enforce .consumes() at runtime,
                // so we check the header manually and throw UnsupportedMediaTypeException
                // which is caught by the dedicated onException handler above.
                .process(exchange -> {
                    String ct = exchange.getIn().getHeader(Exchange.CONTENT_TYPE, String.class);
                    if (ct == null || !ct.contains("application/json")) {
                        throw new UnsupportedMediaTypeException(
                                "Content-Type must be application/json, got: " + ct);
                    }
                })

                // Deserialise JSON → BookingRequest using the Spring ObjectMapper
                // which has JavaTimeModule registered (handles LocalDate / LocalTime).
                // Any Jackson parse / mapping error is re-thrown as IllegalArgumentException
                // so the existing 400-handler above picks it up.
                .process(exchange -> {
                    String json = exchange.getIn().getBody(String.class);
                    try {
                        BookingRequest req = objectMapper.readValue(json, BookingRequest.class);
                        exchange.getIn().setBody(req);
                    } catch (Exception e) {
                        throw new IllegalArgumentException(
                                "Invalid request body: " + e.getMessage(), e);
                    }
                })

                // Guard: required fields must be present
                .choice()
                    .when(simple("${body.classroomId} == null || ${body.classroomId} == ''"))
                        .throwException(new IllegalArgumentException("classroomId must not be blank"))
                    .when(simple("${body.requestedBy} == null || ${body.requestedBy} == ''"))
                        .throwException(new IllegalArgumentException("requestedBy must not be blank"))
                    .when(simple("${body.date} == null"))
                        .throwException(new IllegalArgumentException("date must not be null"))
                    .when(simple("${body.timeSlot} == null"))
                        .throwException(new IllegalArgumentException("timeSlot must not be null"))
                .end()

                .log("[GATEWAY] Validation passed | classroom=${body.classroomId} | date=${body.date}")
                .to("direct:submitBooking");

        // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
        // 5.  INTERNAL ROUTE: Submit Booking → service-booking via HTTP
        // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

        from("direct:submitBooking")
                .routeId("submit-booking-route")

                // Log the outbound request
                .log("[GATEWAY] Forwarding booking request | classroom=${body.classroomId} "
                        + "| date=${body.date} | slot=${body.timeSlot} "
                        + "| requestedBy=${body.requestedBy}")

                // ── Prepare HTTP headers for downstream call ────────────────
                // Remove inbound HTTP metadata so it doesn't contaminate the outbound call
                .removeHeader("CamelHttpPath")
                .removeHeader("CamelHttpQuery")
                .removeHeader("CamelHttpUri")
                .removeHeader("breadcrumbId")
                .setHeader(Exchange.HTTP_METHOD, constant("POST"))
                .setHeader(Exchange.CONTENT_TYPE, constant("application/json"))

                // ── Serialise BookingRequest POJO → JSON string (HTTP body) ────
                // Camel HTTP component needs String/bytes, not a Java object.
                // Use the Spring ObjectMapper (has JavaTimeModule) for serialisation.
                .process(exchange -> {
                    BookingRequest req = exchange.getIn().getBody(BookingRequest.class);
                    exchange.getIn().setBody(objectMapper.writeValueAsString(req));
                })

                // ── Forward to service-booking ──────────────────────────────
                // bridgeEndpoint=true: prevents Camel from re-routing based on Location header
                // throwExceptionOnFailure=false: handle non-2xx ourselves in the choice() below
                .toD(bookingServiceUrl + "/bookings"
                        + "?bridgeEndpoint=true"
                        + "&throwExceptionOnFailure=false"
                        + "&connectTimeout=5000"
                        + "&socketTimeout=10000")

                // ── Response handling ───────────────────────────────────────
                .log("[GATEWAY] Received response from service-booking | HTTP ${header.CamelHttpResponseCode}")

                .choice()
                    .when(header(Exchange.HTTP_RESPONSE_CODE).isEqualTo(202))
                        // Happy path: pass the 202 response body back to the client
                        .log("[GATEWAY] Booking accepted | setting response 202")
                        .setHeader(Exchange.HTTP_RESPONSE_CODE, constant(202))

                    .when(header(Exchange.HTTP_RESPONSE_CODE).isEqualTo(400))
                        // Booking service validation error; proxy it back
                        .log(LoggingLevel.WARN, "[GATEWAY] Booking validation rejected by service (400)")
                        .setHeader(Exchange.HTTP_RESPONSE_CODE, constant(400))

                    .otherwise()
                        // Unexpected response from service-booking
                        .log(LoggingLevel.WARN,
                                "[GATEWAY] Unexpected response code: ${header.CamelHttpResponseCode}")
                        .setHeader(Exchange.HTTP_RESPONSE_CODE, constant(502))
                        .setHeader(Exchange.CONTENT_TYPE, constant("application/json"))
                        .setBody(constant(
                                "{\"error\":\"Upstream service returned an unexpected response.\"}"))
                .end();

        // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
        // 6.  INTERNAL ROUTE: Gateway info (GET /bookings)
        // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

        from("direct:gatewayInfo")
                .routeId("gateway-info-route")
                .setHeader(Exchange.HTTP_RESPONSE_CODE, constant(200))
                .setHeader(Exchange.CONTENT_TYPE, constant("application/json"))
                .setBody(constant(
                        "{\"service\":\"api-gateway-camel\","
                        + "\"status\":\"UP\","
                        + "\"description\":\"Use POST /bookings to submit a booking request.\"}"));
    }
}
