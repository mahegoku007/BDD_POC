package com.classroom.booking.exception;

import com.classroom.common.dto.ErrorResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Centralised exception handler for the Booking Service.
 *
 * <p>Translates exceptions thrown by {@link com.classroom.booking.controller.BookingController}
 * or the service layer into the standardised {@link ErrorResponse} JSON format.
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    /**
     * Handles bean-validation failures (e.g. missing classroomId, invalid e-mail).
     * Returns {@code 400 Bad Request} with a list of field-level errors.
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleValidationException(
            MethodArgumentNotValidException ex) {

        List<ErrorResponse.FieldError> fieldErrors = ex.getBindingResult()
                .getFieldErrors()
                .stream()
                .map(fe -> ErrorResponse.FieldError.builder()
                        .field(fe.getField())
                        .message(fe.getDefaultMessage())
                        .build())
                .toList();

        log.warn("[BOOKING-ERROR] Validation failed: {}", fieldErrors);

        ErrorResponse body = ErrorResponse.builder()
                .timestamp(LocalDateTime.now())
                .status(HttpStatus.BAD_REQUEST.value())
                .error("Bad Request")
                .message("Validation failed for one or more fields")
                .fieldErrors(fieldErrors)
                .build();

        return ResponseEntity.badRequest().body(body);
    }

    /**
     * Handles malformed JSON or unreadable request bodies.
     * Returns {@code 400 Bad Request}.
     */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ErrorResponse> handleNotReadable(
            HttpMessageNotReadableException ex) {

        log.warn("[BOOKING-ERROR] Unreadable request body: {}", ex.getMessage());

        ErrorResponse body = ErrorResponse.builder()
                .timestamp(LocalDateTime.now())
                .status(HttpStatus.BAD_REQUEST.value())
                .error("Bad Request")
                .message("Request body is missing or malformed")
                .build();

        return ResponseEntity.badRequest().body(body);
    }

    /**
     * Handles domain-level validation errors thrown by the service layer
     * (e.g. endTime before startTime).
     * Returns {@code 400 Bad Request}.
     */
    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ErrorResponse> handleIllegalArgument(
            IllegalArgumentException ex) {

        log.warn("[BOOKING-ERROR] Illegal argument: {}", ex.getMessage());

        ErrorResponse body = ErrorResponse.builder()
                .timestamp(LocalDateTime.now())
                .status(HttpStatus.BAD_REQUEST.value())
                .error("Bad Request")
                .message(ex.getMessage())
                .build();

        return ResponseEntity.badRequest().body(body);
    }

    /**
     * Catch-all handler for unexpected server errors.
     * Returns {@code 500 Internal Server Error}.
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleGenericException(Exception ex) {

        log.error("[BOOKING-ERROR] Unexpected error: {}", ex.getMessage(), ex);

        ErrorResponse body = ErrorResponse.builder()
                .timestamp(LocalDateTime.now())
                .status(HttpStatus.INTERNAL_SERVER_ERROR.value())
                .error("Internal Server Error")
                .message("An unexpected error occurred. Please try again later.")
                .build();

        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(body);
    }
}

