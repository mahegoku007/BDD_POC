package com.classroom.common.dto;

import com.classroom.common.enums.AvailabilityStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;

/**
 * Internal result produced by the availability service after checking
 * whether a time slot is free.
 *
 * <p>This DTO is <em>not</em> published to RabbitMQ directly; it is used
 * internally within the availability service to carry the result of an
 * overlap check before constructing a {@link BookingResponse}.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AvailabilityCheckResponse {

    /** Booking identifier from the original {@link BookingRequest}. */
    private String bookingId;

    /** Classroom that was checked. */
    private String classroomId;

    /** Date that was checked. */
    private LocalDate date;

    /** Time slot that was checked. */
    private TimeSlot timeSlot;

    /** Result of the overlap check. */
    private AvailabilityStatus status;

    /**
     * Populated only when {@code status == UNAVAILABLE}.
     * Example: {@code "Time slot is already booked"}
     */
    private String conflictReason;

    /**
     * Convenience factory – {@code AVAILABLE} result with no conflict reason.
     */
    public static AvailabilityCheckResponse available(String bookingId,
                                                      String classroomId,
                                                      LocalDate date,
                                                      TimeSlot timeSlot) {
        return AvailabilityCheckResponse.builder()
                .bookingId(bookingId)
                .classroomId(classroomId)
                .date(date)
                .timeSlot(timeSlot)
                .status(AvailabilityStatus.AVAILABLE)
                .build();
    }

    /**
     * Convenience factory – {@code UNAVAILABLE} result with reason.
     */
    public static AvailabilityCheckResponse unavailable(String bookingId,
                                                        String classroomId,
                                                        LocalDate date,
                                                        TimeSlot timeSlot,
                                                        String reason) {
        return AvailabilityCheckResponse.builder()
                .bookingId(bookingId)
                .classroomId(classroomId)
                .date(date)
                .timeSlot(timeSlot)
                .status(AvailabilityStatus.UNAVAILABLE)
                .conflictReason(reason)
                .build();
    }
}

