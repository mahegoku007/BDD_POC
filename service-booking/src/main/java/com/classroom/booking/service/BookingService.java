package com.classroom.booking.service;

import com.classroom.booking.messaging.BookingProducer;
import com.classroom.common.dto.BookingRequest;
import com.classroom.common.enums.BookingStatus;
import com.classroom.common.util.BookingIdGenerator;
import com.classroom.common.util.TimeSlotUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * Core business logic for the Booking Service.
 *
 * <p>Responsibilities:
 * <ol>
 *   <li>Validate the time slot (end must be after start)</li>
 *   <li>Assign a unique {@code bookingId}</li>
 *   <li>Set status to {@link BookingStatus#PENDING}</li>
 *   <li>Delegate publishing to {@link BookingProducer}</li>
 * </ol>
 *
 * <p>This service does <em>not</em> check availability – that is done
 * asynchronously by {@code service-availability} after consuming the event.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class BookingService {

    private final BookingProducer bookingProducer;

    /**
     * Processes an inbound booking request:
     * assigns an ID, marks it PENDING, and publishes the event.
     *
     * @param request the request received from the REST layer (bookingId may be null)
     * @return the same request enriched with {@code bookingId} and {@code PENDING} status
     * @throws IllegalArgumentException if the time slot is structurally invalid
     *                                  (e.g. endTime before startTime)
     */
    public BookingRequest submitBooking(BookingRequest request) {

        // ── 1. Validate time slot ─────────────────────────────────────────────
        if (!TimeSlotUtils.isValid(request.getTimeSlot())) {
            log.warn("[BOOKING-SERVICE] Rejected invalid time slot: {}", request.getTimeSlot());
            throw new IllegalArgumentException(
                    "endTime must be after startTime. Received: " + request.getTimeSlot());
        }

        // ── 2. Assign booking ID if not already present ───────────────────────
        if (request.getBookingId() == null || request.getBookingId().isBlank()) {
            request.setBookingId(BookingIdGenerator.generate());
        }

        // ── 3. Mark as PENDING ────────────────────────────────────────────────
        request.setStatus(BookingStatus.PENDING);

        log.info("[BOOKING-SERVICE] Submitting booking | bookingId={} | classroom={} | date={} | slot={} | requestedBy={}",
                request.getBookingId(),
                request.getClassroomId(),
                request.getDate(),
                request.getTimeSlot(),
                request.getRequestedBy());

        // ── 4. Publish to RabbitMQ ────────────────────────────────────────────
        bookingProducer.publishBookingRequest(request);

        log.info("[BOOKING-SERVICE] Booking submitted successfully | bookingId={}", request.getBookingId());

        return request;
    }
}

