package com.classroom.booking.controller;

import com.classroom.booking.service.BookingService;
import com.classroom.common.dto.BookingRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * REST controller for the Booking Service.
 *
 * <p>This endpoint is <strong>internal</strong>: it is not directly exposed to
 * external clients. The API Gateway (Camel route in {@code api-gateway-camel})
 * proxies incoming {@code POST /bookings} requests here via HTTP.
 *
 * <p>Flow:
 * <pre>
 *   Client  →  Camel Gateway (:8080)  →  POST /bookings (:8081)  →  RabbitMQ
 * </pre>
 *
 * <p>Returns {@code 202 Accepted} immediately because availability checking
 * is asynchronous (handled by {@code service-availability}).
 */
@Slf4j
@RestController
@RequestMapping("/bookings")
@RequiredArgsConstructor
public class BookingController {

    private final BookingService bookingService;

    /**
     * Accepts a new classroom booking request.
     *
     * Bean-validation is applied to the request body via {@code @Valid}.
     * Validation errors are handled by the {@code GlobalExceptionHandler}.
     *
     * @param bookingRequest the incoming booking request
     * @return {@code 202 Accepted} with the enriched request (bookingId + PENDING status)
     */
    @PostMapping
    public ResponseEntity<BookingRequest> createBooking(
            @Valid @RequestBody BookingRequest bookingRequest) {

        log.info("[BOOKING-CONTROLLER] Received booking request | classroom={} | date={} | slot={} | requestedBy={}",
                bookingRequest.getClassroomId(),
                bookingRequest.getDate(),
                bookingRequest.getTimeSlot(),
                bookingRequest.getRequestedBy());

        BookingRequest result = bookingService.submitBooking(bookingRequest);

        log.info("[BOOKING-CONTROLLER] Booking accepted | bookingId={}", result.getBookingId());

        // 202 Accepted: the request has been received; final outcome is asynchronous
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(result);
    }
}

