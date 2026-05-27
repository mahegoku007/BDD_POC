package com.classroom.notification.config;

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
 * RabbitMQ configuration for the Notification Service.
 *
 * <p>Declares the shared exchange and the two queues this service consumes:
 * {@code booking.confirmed} and {@code booking.rejected}.
 *
 * <p>Topology (this service's perspective):
 * <pre>
 *   classroom.booking.exchange ──► booking.confirmed  ──► [NotificationConsumer.handleConfirmed]
 *                              ──► booking.rejected   ──► [NotificationConsumer.handleRejected]
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

    /**
     * Queue this service reads confirmed bookings from.
     */
    @Bean
    public Queue bookingConfirmedQueue() {
        return QueueBuilder.durable(RabbitMQConstants.QUEUE_BOOKING_CONFIRMED).build();
    }

    /**
     * Queue this service reads rejected bookings from.
     */
    @Bean
    public Queue bookingRejectedQueue() {
        return QueueBuilder.durable(RabbitMQConstants.QUEUE_BOOKING_REJECTED).build();
    }

    // ── Bindings ──────────────────────────────────────────────────────────────

    @Bean
    public Binding bookingConfirmedBinding(Queue bookingConfirmedQueue,
                                           TopicExchange bookingExchange) {
        return BindingBuilder.bind(bookingConfirmedQueue)
                .to(bookingExchange)
                .with(RabbitMQConstants.ROUTING_KEY_CONFIRMED);
    }

    @Bean
    public Binding bookingRejectedBinding(Queue bookingRejectedQueue,
                                          TopicExchange bookingExchange) {
        return BindingBuilder.bind(bookingRejectedQueue)
                .to(bookingExchange)
                .with(RabbitMQConstants.ROUTING_KEY_REJECTED);
    }

    // ── JSON message converter ────────────────────────────────────────────────

    /**
     * Jackson converter with JavaTimeModule so {@code LocalDate},
     * {@code LocalTime}, and {@code LocalDateTime} in {@link com.classroom.common.dto.BookingResponse}
     * are handled correctly.
     */
    @Bean
    public Jackson2JsonMessageConverter jackson2JsonMessageConverter() {
        ObjectMapper mapper = new ObjectMapper()
                .registerModule(new JavaTimeModule())
                .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        return new Jackson2JsonMessageConverter(mapper);
    }

    /**
     * Wires the JSON converter into the default {@link RabbitTemplate}.
     */
    @Bean
    public RabbitTemplate rabbitTemplate(ConnectionFactory connectionFactory,
                                         Jackson2JsonMessageConverter converter) {
        RabbitTemplate template = new RabbitTemplate(connectionFactory);
        template.setMessageConverter(converter);
        return template;
    }

    /**
     * Listener container factory used by {@code @RabbitListener} methods.
     * No special concurrency tuning needed – this is a pure consumer, no DB writes.
     */
    @Bean
    public SimpleRabbitListenerContainerFactory rabbitListenerContainerFactory(
            ConnectionFactory connectionFactory,
            Jackson2JsonMessageConverter converter) {

        SimpleRabbitListenerContainerFactory factory = new SimpleRabbitListenerContainerFactory();
        factory.setConnectionFactory(connectionFactory);
        factory.setMessageConverter(converter);
        factory.setConcurrentConsumers(1);
        factory.setMaxConcurrentConsumers(5);
        return factory;
    }
}

