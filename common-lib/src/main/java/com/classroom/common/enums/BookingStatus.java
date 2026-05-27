package com.classroom.common.enums;

/**
 * Represents the lifecycle state of a classroom booking request.
 *
 * <p>State transitions:
 * <pre>
 *   [API receives request] → PENDING
 *       ↓
 *   [Availability service checks slot]
 *       ↓               ↓
 *   CONFIRMED        REJECTED
 * </pre>
 */
public enum BookingStatus {

    /** Booking request has been received and is awaiting an availability check. */
    PENDING,

    /** Availability service confirmed the time slot is free; booking is locked in. */
    CONFIRMED,

    /** Availability service detected a conflict; booking was not created. */
    REJECTED
}

