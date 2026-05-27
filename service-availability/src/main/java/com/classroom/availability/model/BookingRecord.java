package com.classroom.availability.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;

/**
 * JPA entity that persists a <em>confirmed</em> classroom booking.
 *
 * <p>Only confirmed bookings are stored; rejected ones are discarded.
 * This table is the source of truth used by the overlap-detection query.
 *
 * <p>Storage backend: H2 in-memory database (auto-created on startup,
 * dropped on shutdown). Swap {@code application.yml} datasource config
 * to switch to a persistent store in production.
 */
@Entity
@Table(name = "booking_record")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BookingRecord {

    /** Unique booking identifier (e.g. {@code "BK-a3f5c891"}). */
    @Id
    @Column(name = "booking_id", nullable = false, length = 50)
    private String bookingId;

    /** Classroom identifier (e.g. {@code "CR-101"}). */
    @Column(name = "classroom_id", nullable = false, length = 50)
    private String classroomId;

    /** Date of the booking. */
    @Column(name = "booking_date", nullable = false)
    private LocalDate date;

    /**
     * Inclusive start of the reserved time slot.
     * Used in the half-open overlap query: {@code start_time < :endTime}.
     */
    @Column(name = "start_time", nullable = false)
    private LocalTime startTime;

    /**
     * Exclusive end of the reserved time slot.
     * Used in the half-open overlap query: {@code end_time > :startTime}.
     */
    @Column(name = "end_time", nullable = false)
    private LocalTime endTime;

    /** E-mail of the person who made the booking. */
    @Column(name = "requested_by", nullable = false, length = 255)
    private String requestedBy;

    /** UTC timestamp at which this record was created. */
    @Column(name = "confirmed_at", nullable = false)
    private LocalDateTime confirmedAt;
}

