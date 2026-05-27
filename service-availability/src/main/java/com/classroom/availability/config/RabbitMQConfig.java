package com.classroom.availability.config;

import com.classroom.common.util.RabbitMQConstants;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.amqp.rabbit.config.SimpleRabbitListenerContainerFactory;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * RabbitMQ topology and messaging configuration for the Availability Service.
 *
 * <p>Declares all queues and the shared exchange so that:
 * <ul>
 *   <li>The exchange and queues exist even if this service starts first</li>
 *   <li>The listener container factory uses the JSON converter</li>
 * </ul>
 *
 * <p>Topology (this service's perspective):
 * <pre>
 *   classroom.booking.exchange ──► booking.requested  ──► [AvailabilityConsumer]
 *                              ◄── booking.confirmed  ◄── [AvailabilityProducer]
 *                              ◄── booking.rejected   ◄── [AvailabilityProducer]
 * </pre>
 */
@Configuration
public class RabbitMQConfig {

    // ── Exchange ──────────────────────────────────────────────────────────────

    @Bean
    public TopicExchange bookingExchange() {
        return new TopicExchange(RabbitMQConstants.EXCHANGE, true, false);
    }

    // ── Queues ────────────────────────────────────────────────────────────────

    @Bean
    public Queue bookingRequestedQueue() {
        return QueueBuilder.durable(RabbitMQConstants.QUEUE_BOOKING_REQUESTED)
                .withArgument("x-dead-letter-exchange", RabbitMQConstants.DLX)
                .withArgument("x-dead-letter-routing-key", RabbitMQConstants.QUEUE_DEAD_LETTER)
                .build();
    }

    @Bean
    public Queue bookingConfirmedQueue() {
        return QueueBuilder.durable(RabbitMQConstants.QUEUE_BOOKING_CONFIRMED).build();
    }

    @Bean
    public Queue bookingRejectedQueue() {
        return QueueBuilder.durable(RabbitMQConstants.QUEUE_BOOKING_REJECTED).build();
    }

    // ── Bindings ──────────────────────────────────────────────────────────────

    @Bean
    public Binding bookingRequestedBinding(Queue bookingRequestedQueue, TopicExchange bookingExchange) {
        return BindingBuilder.bind(bookingRequestedQueue)
                .to(bookingExchange)
                .with(RabbitMQConstants.ROUTING_KEY_REQUESTED);
    }

    @Bean
    public Binding bookingConfirmedBinding(Queue bookingConfirmedQueue, TopicExchange bookingExchange) {
        return BindingBuilder.bind(bookingConfirmedQueue)
                .to(bookingExchange)
                .with(RabbitMQConstants.ROUTING_KEY_CONFIRMED);
    }

    @Bean
    public Binding bookingRejectedBinding(Queue bookingRejectedQueue, TopicExchange bookingExchange) {
        return BindingBuilder.bind(bookingRejectedQueue)
                .to(bookingExchange)
                .with(RabbitMQConstants.ROUTING_KEY_REJECTED);
    }

    // ── JSON message converter ────────────────────────────────────────────────

    /**
     * Shared Jackson-based AMQP message converter.
     * Registers JavaTimeModule so {@code LocalDate} / {@code LocalTime} work.
     */
    @Bean
    public Jackson2JsonMessageConverter jackson2JsonMessageConverter() {
        ObjectMapper mapper = new ObjectMapper()
                .registerModule(new JavaTimeModule())
                .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        return new Jackson2JsonMessageConverter(mapper);
    }

    /**
     * RabbitTemplate used by AvailabilityProducer.
     * Configured with the JSON converter.
     */
    @Bean
    public RabbitTemplate rabbitTemplate(ConnectionFactory connectionFactory,
                                         Jackson2JsonMessageConverter converter) {
        RabbitTemplate template = new RabbitTemplate(connectionFactory);
        template.setMessageConverter(converter);
        return template;
    }

    /**
     * Listener container factory used by {@code @RabbitListener} in this service.
     * Wires the JSON converter for automatic deserialisation of inbound messages.
     */
    @Bean
    public SimpleRabbitListenerContainerFactory rabbitListenerContainerFactory(
            ConnectionFactory connectionFactory,
            Jackson2JsonMessageConverter converter) {

        SimpleRabbitListenerContainerFactory factory = new SimpleRabbitListenerContainerFactory();
        factory.setConnectionFactory(connectionFactory);
        factory.setMessageConverter(converter);
        // Concurrency: process one message at a time (safe for in-memory H2 store)
        factory.setConcurrentConsumers(1);
        factory.setMaxConcurrentConsumers(3);
        return factory;
    }
}

