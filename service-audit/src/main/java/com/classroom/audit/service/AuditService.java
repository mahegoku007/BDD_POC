package com.classroom.audit.service;

import com.classroom.audit.model.AuditEvent;
import com.classroom.audit.repository.AuditEventRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;

/**
 * Service layer for audit event persistence and retrieval.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AuditService {

    private final AuditEventRepository repository;

    /**
     * Records a new audit event.
     *
     * @param event the audit event to persist
     * @return the saved event with generated ID
     */
    public AuditEvent recordEvent(AuditEvent event) {
        if (event.getTimestamp() == null) {
            event.setTimestamp(Instant.now());
        }
        AuditEvent saved = repository.save(event);
        log.info("[AUDIT-SERVICE] Recorded event | bookingId={} | action={} | source={}",
                saved.getBookingId(), saved.getAction(), saved.getSource());
        return saved;
    }

    /**
     * Retrieves the full audit trail for a booking.
     *
     * @param bookingId the booking to look up
     * @return list of audit events ordered by timestamp ascending
     */
    public List<AuditEvent> getAuditTrail(String bookingId) {
        return repository.findByBookingIdOrderByTimestampAsc(bookingId);
    }

    /**
     * Retrieves audit events by classroom ID.
     *
     * @param classroomId the classroom to look up
     * @return list of events ordered by timestamp descending
     */
    public List<AuditEvent> getByClassroom(String classroomId) {
        return repository.findByClassroomIdOrderByTimestampDesc(classroomId);
    }
}

