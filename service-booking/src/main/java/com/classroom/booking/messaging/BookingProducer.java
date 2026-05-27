package com.classroom.booking.messaging;

import com.classroom.common.dto.BookingRequest;
import com.classroom.common.util.RabbitMQConstants;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;

/**
 * Publishes {@link BookingRequest} events to the {@code booking.requested} RabbitMQ queue.
 *
 * <p>The message is serialised to JSON by the {@code Jackson2JsonMessageConverter}
 * configured in {@link com.classroom.booking.config.RabbitMQConfig}.
 *
 * <p>Message flow:
 * <pre>
 *   BookingProducer → classroom.booking.exchange (routing key: booking.requested)
 *                   → booking.requested queue
 *                   → [consumed by] service-availability
 * </pre>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class BookingProducer {

    private final RabbitTemplate rabbitTemplate;

    /**
     * Publishes a booking request event to the {@code booking.requested} queue.
     *
     * @param bookingRequest the fully populated request (bookingId must already be set)
     */
    public void publishBookingRequest(BookingRequest bookingRequest) {
        log.info("[BOOKING-PRODUCER] Publishing booking request to '{}' | bookingId={} | classroom={} | date={} | slot={} | requestedBy={}",
                RabbitMQConstants.QUEUE_BOOKING_REQUESTED,
                bookingRequest.getBookingId(),
                bookingRequest.getClassroomId(),
                bookingRequest.getDate(),
                bookingRequest.getTimeSlot(),
                bookingRequest.getRequestedBy());

        rabbitTemplate.convertAndSend(
                RabbitMQConstants.EXCHANGE,
                RabbitMQConstants.ROUTING_KEY_REQUESTED,
                bookingRequest);

        log.debug("[BOOKING-PRODUCER] Successfully published bookingId={}", bookingRequest.getBookingId());
    }
}

