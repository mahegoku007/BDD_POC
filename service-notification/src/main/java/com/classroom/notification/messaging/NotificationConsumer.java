package com.classroom.notification.messaging;

import com.classroom.common.dto.BookingResponse;
import com.classroom.common.util.RabbitMQConstants;
import com.classroom.notification.service.NotificationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

/**
 * RabbitMQ consumer for the {@code booking.confirmed} and {@code booking.rejected} queues.
 *
 * <p>Each listener method receives a {@link BookingResponse} deserialised from JSON
 * by the {@code Jackson2JsonMessageConverter} wired in {@code RabbitMQConfig}.
 *
 * <p>Message flow:
 * <pre>
 *   booking.confirmed  →  handleConfirmed()  →  NotificationService.sendConfirmation()
 *   booking.rejected   →  handleRejected()   →  NotificationService.sendRejection()
 * </pre>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class NotificationConsumer {

    private final NotificationService notificationService;

    /**
     * Processes a confirmed-booking event.
     *
     * @param bookingResponse the confirmed booking details
     */
    @RabbitListener(queues = RabbitMQConstants.QUEUE_BOOKING_CONFIRMED)
    public void handleConfirmed(BookingResponse bookingResponse) {
        log.info("[NOTIFICATION-CONSUMER] Received CONFIRMED event | bookingId={} | classroom={} | requestedBy={}",
                bookingResponse.getBookingId(),
                bookingResponse.getClassroomId(),
                bookingResponse.getRequestedBy());

        try {
            notificationService.sendConfirmation(bookingResponse);
        } catch (Exception ex) {
            log.error("[NOTIFICATION-CONSUMER] Failed to process CONFIRMED event | bookingId={} | error={}",
                    bookingResponse.getBookingId(), ex.getMessage(), ex);
            throw ex; // re-throw for retry / DLX handling
        }
    }

    /**
     * Processes a rejected-booking event.
     *
     * @param bookingResponse the rejected booking details (includes rejection reason in message)
     */
    @RabbitListener(queues = RabbitMQConstants.QUEUE_BOOKING_REJECTED)
    public void handleRejected(BookingResponse bookingResponse) {
        log.info("[NOTIFICATION-CONSUMER] Received REJECTED event | bookingId={} | classroom={} | requestedBy={} | reason={}",
                bookingResponse.getBookingId(),
                bookingResponse.getClassroomId(),
                bookingResponse.getRequestedBy(),
                bookingResponse.getMessage());

        try {
            notificationService.sendRejection(bookingResponse);
        } catch (Exception ex) {
            log.error("[NOTIFICATION-CONSUMER] Failed to process REJECTED event | bookingId={} | error={}",
                    bookingResponse.getBookingId(), ex.getMessage(), ex);
            throw ex;
        }
    }
}

