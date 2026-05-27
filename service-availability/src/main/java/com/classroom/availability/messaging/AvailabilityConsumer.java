package com.classroom.availability.messaging;

import com.classroom.availability.service.AvailabilityService;
import com.classroom.common.dto.AvailabilityCheckResponse;
import com.classroom.common.dto.BookingRequest;
import com.classroom.common.dto.BookingResponse;
import com.classroom.common.enums.AvailabilityStatus;
import com.classroom.common.util.BookingResponseFactory;
import com.classroom.common.util.RabbitMQConstants;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

/**
 * RabbitMQ consumer for the {@code booking.requested} queue.
 *
 * <p>Processing flow per message:
 * <pre>
 *  1. Receive BookingRequest (JSON deserialised by Jackson converter)
 *  2. Call AvailabilityService.checkAvailability()
 *  3a. AVAILABLE  → persist booking + publish to booking.confirmed
 *  3b. UNAVAILABLE → publish to booking.rejected (nothing persisted)
 * </pre>
 *
 * <p>The {@code @RabbitListener} uses the container factory configured in
 * {@code RabbitMQConfig} which sets the
 * {@code Jackson2JsonMessageConverter}, so the raw AMQP bytes are
 * automatically deserialised to {@link BookingRequest}.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AvailabilityConsumer {

    private final AvailabilityService availabilityService;
    private final AvailabilityProducer availabilityProducer;

    /**
     * Processes a single booking request event.
     *
     * @param bookingRequest the deserialized request from the queue
     */
    @RabbitListener(queues = RabbitMQConstants.QUEUE_BOOKING_REQUESTED)
    public void handleBookingRequest(BookingRequest bookingRequest) {

        log.info("[AVAILABILITY-CONSUMER] Received booking request | bookingId={} | classroom={} | date={} | slot={} | requestedBy={}",
                bookingRequest.getBookingId(),
                bookingRequest.getClassroomId(),
                bookingRequest.getDate(),
                bookingRequest.getTimeSlot(),
                bookingRequest.getRequestedBy());

        try {
            // ── Step 1: Check for time-slot conflicts ─────────────────────────
            AvailabilityCheckResponse checkResult =
                    availabilityService.checkAvailability(bookingRequest);

            if (checkResult.getStatus() == AvailabilityStatus.AVAILABLE) {
                handleAvailable(bookingRequest);
            } else {
                handleUnavailable(bookingRequest, checkResult.getConflictReason());
            }

        } catch (Exception ex) {
            // Log exception and let the message be requeued / sent to DLX
            log.error("[AVAILABILITY-CONSUMER] Error processing bookingId={}: {}",
                    bookingRequest.getBookingId(), ex.getMessage(), ex);
            throw ex; // re-throw so Spring AMQP can apply retry/DLX policy
        }
    }

    // ── Private helpers ───────────────────────────────────────────────────────

    /**
     * Confirms the booking: persists the record, builds response, publishes to confirmed queue.
     */
    private void handleAvailable(BookingRequest request) {
        // Persist so subsequent requests detect the conflict
        availabilityService.confirmBooking(request);

        BookingResponse response = BookingResponseFactory.confirmed(request);

        log.info("[AVAILABILITY-CONSUMER] Booking CONFIRMED | bookingId={}", request.getBookingId());
        availabilityProducer.publishConfirmed(response);
    }

    /**
     * Rejects the booking: builds response with reason, publishes to rejected queue.
     */
    private void handleUnavailable(BookingRequest request, String reason) {
        BookingResponse response = BookingResponseFactory.rejected(request, reason);

        log.warn("[AVAILABILITY-CONSUMER] Booking REJECTED | bookingId={} | reason={}",
                request.getBookingId(), reason);
        availabilityProducer.publishRejected(response);
    }
}

