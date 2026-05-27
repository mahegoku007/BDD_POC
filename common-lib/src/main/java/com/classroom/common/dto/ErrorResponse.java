package com.classroom.common.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Standardised error envelope returned by all services on validation failures
 * or unexpected errors (HTTP 4xx / 5xx responses).
 *
 * <p>Example JSON:
 * <pre>{@code
 * {
 *   "timestamp": "2026-06-01T09:00:00",
 *   "status": 400,
 *   "error": "Bad Request",
 *   "message": "Validation failed",
 *   "fieldErrors": [
 *     { "field": "classroomId", "message": "must not be blank" }
 *   ]
 * }
 * }</pre>
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ErrorResponse {

    @Builder.Default
    private LocalDateTime timestamp = LocalDateTime.now();

    /** HTTP status code (e.g. 400, 404, 500). */
    private int status;

    /** Short error description (e.g. "Bad Request"). */
    private String error;

    /** Human-readable summary message. */
    private String message;

    /**
     * Per-field validation errors.  Empty (not null) when no field errors exist.
     */
    @Builder.Default
    private List<FieldError> fieldErrors = List.of();

    // ── Inner type ────────────────────────────────────────────────────────────

    /**
     * Describes a single bean-validation failure.
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class FieldError {

        /** Name of the invalid field (e.g. {@code "timeSlot.endTime"}). */
        private String field;

        /** Violation description (e.g. {@code "must not be null"}). */
        private String message;
    }
}

