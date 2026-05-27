package com.classroom.availability.service;

import com.classroom.availability.model.BookingRecord;
import com.classroom.availability.repository.BookingRecordRepository;
import com.classroom.common.dto.AvailabilityCheckResponse;
import com.classroom.common.dto.BookingRequest;
import com.classroom.common.dto.TimeSlot;
import com.classroom.common.enums.AvailabilityStatus;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

/**
 * Core business logic for the Availability Service.
 *
 * <p>Two main operations:
 * <ol>
 *   <li>{@link #checkAvailability} – queries H2 for time-slot conflicts</li>
 *   <li>{@link #confirmBooking}    – persists the booking record after confirmation</li>
 * </ol>
 *
 * <p>Both operations are executed inside a transaction to prevent
 * race conditions when concurrent requests target the same slot.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AvailabilityService {

    /** Conflict message returned to the notification service. */
    public static final String CONFLICT_REASON = "Time slot is already booked";

    private final BookingRecordRepository repository;

    // ── Availability check ────────────────────────────────────────────────────

    /**
     * Checks whether the requested time slot is free.
     *
     * @param request the booking request to evaluate
     * @return {@link AvailabilityCheckResponse} with
     *         {@link AvailabilityStatus#AVAILABLE} or
     *         {@link AvailabilityStatus#UNAVAILABLE}
     */
    @Transactional(readOnly = true)
    public AvailabilityCheckResponse checkAvailability(BookingRequest request) {
        TimeSlot slot = request.getTimeSlot();

        log.info("[AVAILABILITY-SERVICE] Checking availability | bookingId={} | classroom={} | date={} | slot={}",
                request.getBookingId(), request.getClassroomId(), request.getDate(), slot);

        boolean conflict = repository.existsOverlappingBooking(
                request.getClassroomId(),
                request.getDate(),
                slot.getStartTime(),
                slot.getEndTime());

        if (conflict) {
            log.warn("[AVAILABILITY-SERVICE] Conflict detected | bookingId={} | classroom={} | date={} | slot={}",
                    request.getBookingId(), request.getClassroomId(), request.getDate(), slot);

            return AvailabilityCheckResponse.unavailable(
                    request.getBookingId(),
                    request.getClassroomId(),
                    request.getDate(),
                    slot,
                    CONFLICT_REASON);
        }

        log.info("[AVAILABILITY-SERVICE] Slot is available | bookingId={}", request.getBookingId());

        return AvailabilityCheckResponse.available(
                request.getBookingId(),
                request.getClassroomId(),
                request.getDate(),
                slot);
    }

    // ── Booking persistence ───────────────────────────────────────────────────

    /**
     * Saves the confirmed booking to the H2 store so that subsequent requests
     * for the same slot will detect a conflict.
     *
     * <p>This method is called <em>only after</em> {@link #checkAvailability}
     * returns {@link AvailabilityStatus#AVAILABLE} to avoid persisting rejected bookings.
     *
     * @param request the booking request that was confirmed
     * @return the persisted {@link BookingRecord}
     */
    @Transactional
    public BookingRecord confirmBooking(BookingRequest request) {
        TimeSlot slot = request.getTimeSlot();

        BookingRecord record = BookingRecord.builder()
                .bookingId(request.getBookingId())
                .classroomId(request.getClassroomId())
                .date(request.getDate())
                .startTime(slot.getStartTime())
                .endTime(slot.getEndTime())
                .requestedBy(request.getRequestedBy())
                .confirmedAt(LocalDateTime.now())
                .build();

        BookingRecord saved = repository.save(record);

        log.info("[AVAILABILITY-SERVICE] Booking persisted | bookingId={} | classroom={} | date={} | slot={}",
                saved.getBookingId(), saved.getClassroomId(), saved.getDate(), slot);

        return saved;
    }

    // ── Test helper ───────────────────────────────────────────────────────────

    /**
     * Saves a booking record directly (used by integration tests to pre-populate
     * the store with existing bookings).
     *
     * @param record the record to save
     */
    @Transactional
    public void saveRecord(BookingRecord record) {
        repository.save(record);
    }
}

