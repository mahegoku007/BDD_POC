package com.classroom.common.util;

/**
 * Single source of truth for all RabbitMQ exchange, queue, and routing-key names
 * used across the classroom booking system.
 *
 * <p>Every service that produces or consumes messages must reference these constants
 * instead of hard-coding strings, so a rename only requires a change here.
 *
 * <p>Topology overview:
 * <pre>
 *
 *  [API Gateway / Booking Service]
 *          │
 *          │  routing key: booking.requested
 *          ▼
 *  Exchange: classroom.booking.exchange  (topic)
 *          │
 *          ├──► Queue: booking.requested      → Availability Service
 *          │
 *          ├──► Queue: booking.confirmed      → Notification Service
 *          │
 *          └──► Queue: booking.rejected       → Notification Service
 *
 * </pre>
 */
public final class RabbitMQConstants {

    private RabbitMQConstants() {}

    // ── Exchange ──────────────────────────────────────────────────────────────

    /** Topic exchange that receives all booking-related events. */
    public static final String EXCHANGE = "classroom.booking.exchange";

    // ── Queues ────────────────────────────────────────────────────────────────

    /** Queue consumed by the <em>availability</em> service. */
    public static final String QUEUE_BOOKING_REQUESTED  = "booking.requested";

    /** Queue consumed by the <em>notification</em> service (happy path). */
    public static final String QUEUE_BOOKING_CONFIRMED  = "booking.confirmed";

    /** Queue consumed by the <em>notification</em> service (rejected path). */
    public static final String QUEUE_BOOKING_REJECTED   = "booking.rejected";

    // ── Routing keys ──────────────────────────────────────────────────────────

    /** Routing key used when publishing to {@link #QUEUE_BOOKING_REQUESTED}. */
    public static final String ROUTING_KEY_REQUESTED    = "booking.requested";

    /** Routing key used when publishing to {@link #QUEUE_BOOKING_CONFIRMED}. */
    public static final String ROUTING_KEY_CONFIRMED    = "booking.confirmed";

    /** Routing key used when publishing to {@link #QUEUE_BOOKING_REJECTED}. */
    public static final String ROUTING_KEY_REJECTED     = "booking.rejected";

    // ── Dead-letter exchange (optional / future use) ──────────────────────────

    /** Dead-letter exchange for messages that could not be processed. */
    public static final String DLX = "classroom.booking.dlx";

    /** Dead-letter queue for unprocessable messages. */
    public static final String QUEUE_DEAD_LETTER = "booking.dead-letter";
}

