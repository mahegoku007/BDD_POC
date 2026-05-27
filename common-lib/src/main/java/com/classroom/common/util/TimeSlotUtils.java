package com.classroom.common.util;

import com.classroom.common.dto.TimeSlot;

import java.time.Duration;
import java.time.LocalTime;

/**
 * Stateless utility methods for {@link TimeSlot} validation and overlap detection.
 *
 * <p><strong>Overlap rule (half-open intervals):</strong>
 * Two slots {@code A} and {@code B} overlap when:
 * <pre>
 *   A.start < B.end  AND  B.start < A.end
 * </pre>
 * A slot that starts exactly when another ends is considered <em>adjacent</em>,
 * not overlapping (e.g. 09:00–10:00 and 10:00–11:00 do NOT conflict).
 */
public final class TimeSlotUtils {

    // Utility class – no instances
    private TimeSlotUtils() {}

    // ── Validation ────────────────────────────────────────────────────────────

    /**
     * Returns {@code true} when the slot is structurally valid:
     * both times are non-null and {@code startTime} is strictly before {@code endTime}.
     *
     * @param slot the time slot to validate (may be {@code null})
     * @return {@code true} if valid; {@code false} otherwise
     */
    public static boolean isValid(TimeSlot slot) {
        if (slot == null || slot.getStartTime() == null || slot.getEndTime() == null) {
            return false;
        }
        return slot.getStartTime().isBefore(slot.getEndTime());
    }

    // ── Overlap detection ─────────────────────────────────────────────────────

    /**
     * Returns {@code true} when two time slots overlap (share at least one instant).
     *
     * <p>Adjacent slots (end of one == start of the other) are <em>not</em> overlapping.
     *
     * @param a first slot
     * @param b second slot
     * @return {@code true} if the slots overlap; {@code false} otherwise
     * @throws IllegalArgumentException if either slot is invalid according to {@link #isValid}
     */
    public static boolean overlaps(TimeSlot a, TimeSlot b) {
        if (!isValid(a) || !isValid(b)) {
            throw new IllegalArgumentException(
                    "Cannot check overlap: one or both TimeSlot objects are invalid. a=" + a + ", b=" + b);
        }
        // Half-open interval overlap:  a.start < b.end  &&  b.start < a.end
        return a.getStartTime().isBefore(b.getEndTime())
                && b.getStartTime().isBefore(a.getEndTime());
    }

    /**
     * Convenience overload: checks overlap using raw {@link LocalTime} values.
     *
     * @param startA start of the first slot (inclusive)
     * @param endA   end of the first slot   (exclusive)
     * @param startB start of the second slot (inclusive)
     * @param endB   end of the second slot   (exclusive)
     * @return {@code true} if the slots overlap
     */
    public static boolean overlaps(LocalTime startA, LocalTime endA,
                                   LocalTime startB, LocalTime endB) {
        return overlaps(new TimeSlot(startA, endA), new TimeSlot(startB, endB));
    }

    // ── Duration helpers ──────────────────────────────────────────────────────

    /**
     * Returns the duration of the slot in minutes.
     *
     * @param slot a valid time slot
     * @return duration in whole minutes
     */
    public static long durationMinutes(TimeSlot slot) {
        if (!isValid(slot)) {
            throw new IllegalArgumentException("Invalid TimeSlot: " + slot);
        }
        return Duration.between(slot.getStartTime(), slot.getEndTime()).toMinutes();
    }
}

