package com.classroom.availability.messaging;

import com.classroom.common.dto.BookingResponse;
import com.classroom.common.util.RabbitMQConstants;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;

/**
 * Publishes availability outcomes to RabbitMQ.
 *
 * <p>Routes:
 * <ul>
 *   <li>CONFIRMED → {@code classroom.booking.exchange} / routing key {@code booking.confirmed}</li>
 *   <li>REJECTED  → {@code classroom.booking.exchange} / routing key {@code booking.rejected}</li>
 * </ul>
 *
 * <p>The {@link BookingResponse} payload is serialised to JSON by
 * the {@code Jackson2JsonMessageConverter} configured in {@code RabbitMQConfig}.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AvailabilityProducer {

    private final RabbitTemplate rabbitTemplate;

    /**
     * Publishes a confirmed booking event to the {@code booking.confirmed} queue.
     *
     * @param response the fully built confirmation response
     */
    public void publishConfirmed(BookingResponse response) {
        log.info("[AVAILABILITY-PRODUCER] Publishing CONFIRMED | bookingId={} | classroom={} | date={} | requestedBy={}",
                response.getBookingId(), response.getClassroomId(),
                response.getDate(), response.getRequestedBy());

        rabbitTemplate.convertAndSend(
                RabbitMQConstants.EXCHANGE,
                RabbitMQConstants.ROUTING_KEY_CONFIRMED,
                response);

        log.debug("[AVAILABILITY-PRODUCER] CONFIRMED event published | bookingId={}", response.getBookingId());
    }

    /**
     * Publishes a rejected booking event to the {@code booking.rejected} queue.
     *
     * @param response the fully built rejection response (contains reason in message)
     */
    public void publishRejected(BookingResponse response) {
        log.info("[AVAILABILITY-PRODUCER] Publishing REJECTED | bookingId={} | classroom={} | date={} | reason={}",
                response.getBookingId(), response.getClassroomId(),
                response.getDate(), response.getMessage());

        rabbitTemplate.convertAndSend(
                RabbitMQConstants.EXCHANGE,
                RabbitMQConstants.ROUTING_KEY_REJECTED,
                response);

        log.debug("[AVAILABILITY-PRODUCER] REJECTED event published | bookingId={}", response.getBookingId());
    }
}

