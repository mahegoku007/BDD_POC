package com.classroom.gateway.route;

import com.classroom.gateway.exception.UnsupportedMediaTypeException;
import org.apache.camel.Exchange;
import org.apache.camel.LoggingLevel;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.model.rest.RestBindingMode;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.net.ConnectException;

/**
 * REST configuration and exception handling for the API Gateway.
 *
 * <p>Route logic (validate, audit, submit, batch) is defined in XML DSL
 * at {@code classpath:camel/routes.xml} and loaded via the
 * {@code camel.springboot.routes-include-pattern} property.
 *
 * <p>This class only defines:
 * <ul>
 *   <li>REST DSL configuration (platform-http, CORS, binding mode)</li>
 *   <li>REST endpoint declarations (POST /bookings, GET /bookings, POST /bookings/batch)</li>
 *   <li>Global onException handlers</li>
 * </ul>
 */
@Component
public class BookingRoute extends RouteBuilder {

    /** Route Slip pipeline — driven by ConfigMap (ROUTE_SLIP_PIPELINE env var). */
    @Value("${gateway.route-slip:direct:submitBooking}")
    private String routeSlipPipeline;

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
        // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

        restConfiguration()
                .component("platform-http")
                .bindingMode(RestBindingMode.off)
                .enableCORS(true)
                .corsHeaderProperty("Access-Control-Allow-Origin", "*")
                .corsHeaderProperty("Access-Control-Allow-Headers",
                        "Content-Type, Accept, Authorization");

        // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
        // 3.  REST ENDPOINTS
        //     Routing logic is in camel/routes.xml (loaded via routes-include-pattern)
        // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

        rest("/bookings")
                .description("Classroom Booking API")

                // ── POST /bookings ──────────────────────────────────────────
                .post()
                    .description("Submit a new classroom booking request")
                    .consumes("application/json")
                    .produces("application/json")
                    .to("direct:validateBooking")

                // ── GET /bookings ───────────────────────────────────────────
                .get()
                    .description("Gateway info endpoint")
                    .produces("application/json")
                    .to("direct:gatewayInfo")

                // ── POST /bookings/batch ────────────────────────────────────
                .post("/batch")
                    .description("Submit a batch of booking requests (JSON array)")
                    .consumes("application/json")
                    .produces("application/json")
                    .to("direct:batchBooking");

        // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
        // 4.  ROUTE SLIP DISPATCHER
        //     Implements Dynamic Route Slip EIP — the pipeline is driven by
        //     the ConfigMap value of ROUTE_SLIP_PIPELINE.
        //     e.g. "direct:audit,direct:submitBooking"
        // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

        from("direct:routeSlipDispatcher")
                .routeId("route-slip-dispatcher")
                .log("[GATEWAY] Route Slip pipeline: " + routeSlipPipeline)
                .setHeader("routeSlipPipeline", constant(routeSlipPipeline))
                .routingSlip(header("routeSlipPipeline"));
    }
}
