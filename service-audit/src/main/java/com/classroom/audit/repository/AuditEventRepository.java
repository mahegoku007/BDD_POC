package com.classroom.audit.repository;

import com.classroom.audit.model.AuditEvent;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * MongoDB repository for audit events.
 */
@Repository
public interface AuditEventRepository extends MongoRepository<AuditEvent, String> {

    /** Find all audit events for a given booking, ordered by timestamp ascending. */
    List<AuditEvent> findByBookingIdOrderByTimestampAsc(String bookingId);

    /** Find all audit events for a given classroom. */
    List<AuditEvent> findByClassroomIdOrderByTimestampDesc(String classroomId);
}

