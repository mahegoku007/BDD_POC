package com.classroom.audit.controller;

import com.classroom.audit.model.AuditEvent;
import com.classroom.audit.service.AuditService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * REST controller for audit event operations.
 * Called by the API Gateway's route-slip pipeline via HTTP POST.
 */
@Slf4j
@RestController
@RequestMapping("/audit")
@RequiredArgsConstructor
public class AuditController {

    private final AuditService auditService;
    private final ObjectMapper objectMapper;

    /**
     * Records a booking audit event.
     * Called from the Camel route-slip: POST /audit/bookings
     *
     * @param body JSON payload (BookingRequest) from the gateway
     * @return 201 Created with the saved audit event
     */
    @PostMapping("/bookings")
    public ResponseEntity<AuditEvent> recordBookingEvent(@RequestBody String body) {
        try {
            JsonNode json = objectMapper.readTree(body);

            String bookingId = json.path("bookingId").asText(null);
            String classroomId = json.path("classroomId").asText(null);
            String requestedBy = json.path("requestedBy").asText(null);
            String status = json.path("status").asText("CREATED");

            // Determine action from status field
            String action = mapStatusToAction(status);

            AuditEvent event = AuditEvent.builder()
                    .bookingId(bookingId)
                    .classroomId(classroomId)
                    .requestedBy(requestedBy)
                    .action(action)
                    .payload(body)
                    .source("api-gateway")
                    .build();

            AuditEvent saved = auditService.recordEvent(event);
            return ResponseEntity.status(HttpStatus.CREATED).body(saved);

        } catch (Exception e) {
            log.error("[AUDIT-SERVICE] Failed to record audit event: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    /**
     * Retrieves the full audit trail for a booking.
     *
     * @param bookingId the booking ID to look up
     * @return 200 with list of audit events, or 404 if none found
     */
    @GetMapping("/bookings/{bookingId}")
    public ResponseEntity<List<AuditEvent>> getAuditTrail(@PathVariable String bookingId) {
        List<AuditEvent> trail = auditService.getAuditTrail(bookingId);
        if (trail.isEmpty()) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(trail);
    }

    /**
     * Retrieves audit events for a specific classroom.
     */
    @GetMapping("/classrooms/{classroomId}")
    public ResponseEntity<List<AuditEvent>> getByClassroom(@PathVariable String classroomId) {
        List<AuditEvent> events = auditService.getByClassroom(classroomId);
        return ResponseEntity.ok(events);
    }

    private String mapStatusToAction(String status) {
        return switch (status.toUpperCase()) {
            case "PENDING" -> "CREATED";
            case "CONFIRMED" -> "CONFIRMED";
            case "REJECTED" -> "REJECTED";
            default -> "CREATED";
        };
    }
}

