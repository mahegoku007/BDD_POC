package com.classroom.audit.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;

/**
 * MongoDB document representing a single audit event for a booking.
 * Multiple events can exist per bookingId (CREATED, CONFIRMED, REJECTED, etc.).
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Document(collection = "audit_events")
public class AuditEvent {

    @Id
    private String id;

    /** Booking ID this event relates to. */
    @Indexed
    private String bookingId;

    /** Action/lifecycle event: CREATED, CONFIRMED, REJECTED, CANCELLED. */
    private String action;

    /** Classroom ID for quick filtering. */
    private String classroomId;

    /** Who triggered this event. */
    private String requestedBy;

    /** Full JSON payload snapshot at the time of the event. */
    private String payload;

    /** When this event was recorded. */
    @Builder.Default
    private Instant timestamp = Instant.now();

    /** Source service that triggered this event. */
    private String source;
}

