package com.classroom.common.enums;

/**
 * Result of an availability check performed by the availability service.
 *
 * <p>Used in {@code AvailabilityCheckResponse} and routing decisions
 * inside the availability service before publishing to RabbitMQ.
 */
public enum AvailabilityStatus {

    /** The requested time slot is free – booking may proceed. */
    AVAILABLE,

    /** The requested time slot overlaps an existing booking – booking must be rejected. */
    UNAVAILABLE
}

