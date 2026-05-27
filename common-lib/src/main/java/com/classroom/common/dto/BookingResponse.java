package com.classroom.common.dto;

import com.classroom.common.enums.BookingStatus;
import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * Published to either {@code booking.confirmed} or {@code booking.rejected}
 * by the availability service after processing a {@link BookingRequest}.
 *
 * <p>Also returned as the HTTP response body by the API gateway when the
 * client polls for booking status (optional extension).
 *
 * <p>Jackson serialisation notes:
 * <ul>
 *   <li>{@code date}      → {@code "yyyy-MM-dd"}</li>
 *   <li>{@code timestamp} → {@code "yyyy-MM-dd'T'HH:mm:ss"}</li>
 * </ul>
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BookingResponse {

    /** Booking identifier – mirrors {@link BookingRequest#getBookingId()}. */
    private String bookingId;

    /** Classroom that was requested. */
    private String classroomId;

    /** Date of the requested booking. */
    @JsonFormat(pattern = "yyyy-MM-dd")
    private LocalDate date;

    /** Requested time slot. */
    private TimeSlot timeSlot;

    /** E-mail of the requester – passed through for the notification service. */
    private String requestedBy;

    /**
     * Final status of the booking: {@code CONFIRMED} or {@code REJECTED}.
     * Never {@code PENDING} in a response event.
     */
    private BookingStatus status;

    /**
     * Human-readable message:
     * <ul>
     *   <li>On {@code CONFIRMED}: e.g. {@code "Your booking CR-101 on 2026-06-01 is CONFIRMED"}</li>
     *   <li>On {@code REJECTED}:  e.g. {@code "Time slot is already booked"}</li>
     * </ul>
     */
    private String message;

    /**
     * UTC timestamp at which the availability service processed the request.
     */
    @Builder.Default
    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss")
    private LocalDateTime timestamp = LocalDateTime.now();
}

