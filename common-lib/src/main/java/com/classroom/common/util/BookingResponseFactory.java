package com.classroom.common.util;

import com.classroom.common.dto.BookingRequest;
import com.classroom.common.dto.BookingResponse;
import com.classroom.common.dto.TimeSlot;
import com.classroom.common.enums.BookingStatus;

import java.time.LocalDateTime;

/**
 * Factory methods for building {@link BookingResponse} objects from a
 * {@link BookingRequest}.
 *
 * <p>Centralising response construction here ensures all services produce
 * consistent message payloads without duplicating field-mapping logic.
 */
public final class BookingResponseFactory {

    private BookingResponseFactory() {}

    /**
     * Creates a {@code CONFIRMED} response from the original request.
     *
     * @param request the original booking request (must have a non-null {@code bookingId})
     * @return a response with status {@link BookingStatus#CONFIRMED}
     */
    public static BookingResponse confirmed(BookingRequest request) {
        return buildResponse(
                request,
                BookingStatus.CONFIRMED,
                String.format("Your booking %s for %s on %s (%s) is CONFIRMED",
                        request.getBookingId(),
                        request.getClassroomId(),
                        request.getDate(),
                        request.getTimeSlot()));
    }

    /**
     * Creates a {@code REJECTED} response with the supplied reason.
     *
     * @param request the original booking request
     * @param reason  human-readable explanation (e.g. {@code "Time slot is already booked"})
     * @return a response with status {@link BookingStatus#REJECTED}
     */
    public static BookingResponse rejected(BookingRequest request, String reason) {
        return buildResponse(
                request,
                BookingStatus.REJECTED,
                String.format("Your booking request for %s on %s has been REJECTED – %s",
                        request.getClassroomId(),
                        request.getDate(),
                        reason));
    }

    // ── Internal helper ───────────────────────────────────────────────────────

    private static BookingResponse buildResponse(BookingRequest request,
                                                 BookingStatus status,
                                                 String message) {
        return BookingResponse.builder()
                .bookingId(request.getBookingId())
                .classroomId(request.getClassroomId())
                .date(request.getDate())
                .timeSlot(copyTimeSlot(request.getTimeSlot()))
                .requestedBy(request.getRequestedBy())
                .status(status)
                .message(message)
                .timestamp(LocalDateTime.now())
                .build();
    }

    private static TimeSlot copyTimeSlot(TimeSlot original) {
        if (original == null) return null;
        return new TimeSlot(original.getStartTime(), original.getEndTime());
    }
}

