package com.classroom.common.util;

import java.util.UUID;

/**
 * Centralised generator for unique booking identifiers.
 *
 * <p>All services that create a new {@code bookingId} should call
 * {@link #generate()} so the format can be changed in one place.
 *
 * <p>Current format: {@code "BK-" + first 8 chars of a UUID v4},
 * e.g. {@code "BK-a3f5c891"}.
 */
public final class BookingIdGenerator {

    private BookingIdGenerator() {}

    /**
     * Generates a new, unique booking identifier.
     *
     * @return a non-null, non-empty booking ID string
     */
    public static String generate() {
        // Prefix + first 8 hex chars of a random UUID → low collision probability
        // for the volumes expected in a classroom booking system.
        return "BK-" + UUID.randomUUID().toString().replace("-", "").substring(0, 8);
    }
}

