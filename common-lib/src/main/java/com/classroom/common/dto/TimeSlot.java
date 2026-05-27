package com.classroom.common.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalTime;

/**
 * Represents a half-open time interval [startTime, endTime) for a classroom booking.
 *
 * <p>Boundary rule (enforced by {@link com.classroom.common.util.TimeSlotUtils}):
 * a slot that starts exactly when another ends is considered <em>non-overlapping</em>.
 *
 * <p>Time values are serialised as {@code "HH:mm"} strings in JSON payloads.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TimeSlot {

    /**
     * Inclusive start of the time slot.
     * Example: {@code "09:00"}
     */
    @NotNull(message = "startTime must not be null")
    @JsonFormat(pattern = "HH:mm")
    private LocalTime startTime;

    /**
     * Exclusive end of the time slot.
     * Example: {@code "10:00"}
     */
    @NotNull(message = "endTime must not be null")
    @JsonFormat(pattern = "HH:mm")
    private LocalTime endTime;

    /**
     * Human-readable representation for logging and notifications.
     * Example: {@code "09:00 - 10:00"}
     */
    @Override
    public String toString() {
        return startTime + " - " + endTime;
    }
}

