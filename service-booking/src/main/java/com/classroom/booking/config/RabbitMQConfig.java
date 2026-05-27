package com.classroom.booking.config;

import com.classroom.common.util.RabbitMQConstants;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * RabbitMQ topology configuration for the Booking Service.
 *
 * <p>Declares the shared exchange, the {@code booking.requested} queue,
 * and the binding between them. The same exchange and queue names are
 * used by all services via {@link RabbitMQConstants}.
 *
 * <p>Topology (this service's slice):
 * <pre>
 *   [BookingService] ──► classroom.booking.exchange ──► booking.requested queue
 * </pre>
 */
@Configuration
public class RabbitMQConfig {

    // ── Exchange ──────────────────────────────────────────────────────────────

    /**
     * Shared topic exchange for all classroom booking events.
     * Topic exchange allows routing by pattern (e.g. {@code "booking.*"}).
     */
    @Bean
    public TopicExchange bookingExchange() {
        return new TopicExchange(RabbitMQConstants.EXCHANGE, /* durable */ true, /* autoDelete */ false);
    }

    // ── Queues ────────────────────────────────────────────────────────────────

    /**
     * Durable queue consumed by the Availability Service.
     * Messages survive RabbitMQ restarts.
     */
    @Bean
    public Queue bookingRequestedQueue() {
        return QueueBuilder.durable(RabbitMQConstants.QUEUE_BOOKING_REQUESTED)
                // Route unprocessable messages to the dead-letter exchange
                .withArgument("x-dead-letter-exchange", RabbitMQConstants.DLX)
                .withArgument("x-dead-letter-routing-key", RabbitMQConstants.QUEUE_DEAD_LETTER)
                .build();
    }

    /**
     * Durable queue consumed by the Notification Service (confirmed bookings).
     */
    @Bean
    public Queue bookingConfirmedQueue() {
        return QueueBuilder.durable(RabbitMQConstants.QUEUE_BOOKING_CONFIRMED).build();
    }

    /**
     * Durable queue consumed by the Notification Service (rejected bookings).
     */
    @Bean
    public Queue bookingRejectedQueue() {
        return QueueBuilder.durable(RabbitMQConstants.QUEUE_BOOKING_REJECTED).build();
    }

    // ── Bindings ──────────────────────────────────────────────────────────────

    /**
     * Routes messages with key {@code "booking.requested"} to the requested queue.
     */
    @Bean
    public Binding bookingRequestedBinding(Queue bookingRequestedQueue, TopicExchange bookingExchange) {
        return BindingBuilder.bind(bookingRequestedQueue)
                .to(bookingExchange)
                .with(RabbitMQConstants.ROUTING_KEY_REQUESTED);
    }

    /**
     * Routes messages with key {@code "booking.confirmed"} to the confirmed queue.
     */
    @Bean
    public Binding bookingConfirmedBinding(Queue bookingConfirmedQueue, TopicExchange bookingExchange) {
        return BindingBuilder.bind(bookingConfirmedQueue)
                .to(bookingExchange)
                .with(RabbitMQConstants.ROUTING_KEY_CONFIRMED);
    }

    /**
     * Routes messages with key {@code "booking.rejected"} to the rejected queue.
     */
    @Bean
    public Binding bookingRejectedBinding(Queue bookingRejectedQueue, TopicExchange bookingExchange) {
        return BindingBuilder.bind(bookingRejectedQueue)
                .to(bookingExchange)
                .with(RabbitMQConstants.ROUTING_KEY_REJECTED);
    }

    // ── Jackson message converter ─────────────────────────────────────────────

    /**
     * Converts AMQP message bodies to/from JSON using Jackson.
     * Registers the JavaTimeModule so {@code LocalDate} / {@code LocalTime}
     * serialise correctly.
     */
    @Bean
    public Jackson2JsonMessageConverter jackson2JsonMessageConverter() {
        ObjectMapper mapper = new ObjectMapper()
                .registerModule(new JavaTimeModule())
                .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        return new Jackson2JsonMessageConverter(mapper);
    }

    /**
     * Overrides the default {@link RabbitTemplate} to use the JSON converter
     * so that published messages carry a {@code content_type: application/json} header.
     */
    @Bean
    public RabbitTemplate rabbitTemplate(ConnectionFactory connectionFactory,
                                         Jackson2JsonMessageConverter converter) {
        RabbitTemplate template = new RabbitTemplate(connectionFactory);
        template.setMessageConverter(converter);
        return template;
    }
}

