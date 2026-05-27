package com.classroom.bdd.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.springframework.amqp.core.AmqpAdmin;
import org.springframework.amqp.rabbit.connection.CachingConnectionFactory;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitAdmin;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestTemplate;

/**
 * Spring configuration for the Cucumber integration-test suite.
 *
 * <p>Provides all infrastructure beans required by step definitions:
 * <ul>
 *   <li>{@link RestTemplate} – HTTP calls to all running services</li>
 *   <li>{@link RabbitTemplate} – publish and consume RabbitMQ messages</li>
 *   <li>{@link RabbitAdmin} – inspect queue depths, purge queues</li>
 *   <li>{@link ObjectMapper} – JSON serialisation of DTOs</li>
 * </ul>
 */
@Configuration
public class TestInfrastructureConfig {

    @Value("${spring.rabbitmq.host:localhost}")
    private String rabbitHost;

    @Value("${spring.rabbitmq.port:5672}")
    private int rabbitPort;

    @Value("${spring.rabbitmq.username:guest}")
    private String rabbitUser;

    @Value("${spring.rabbitmq.password:guest}")
    private String rabbitPassword;

    // ── RabbitMQ ──────────────────────────────────────────────────────────────

    @Bean
    public ConnectionFactory connectionFactory() {
        CachingConnectionFactory factory = new CachingConnectionFactory(rabbitHost, rabbitPort);
        factory.setUsername(rabbitUser);
        factory.setPassword(rabbitPassword);
        return factory;
    }

    @Bean
    public Jackson2JsonMessageConverter messageConverter(ObjectMapper objectMapper) {
        return new Jackson2JsonMessageConverter(objectMapper);
    }

    @Bean
    public RabbitTemplate rabbitTemplate(ConnectionFactory connectionFactory,
                                         Jackson2JsonMessageConverter converter) {
        RabbitTemplate template = new RabbitTemplate(connectionFactory);
        template.setMessageConverter(converter);
        // Wait up to 3 s for a reply when using receiveAndConvert
        template.setReceiveTimeout(3_000);
        return template;
    }

    @Bean
    public AmqpAdmin rabbitAdmin(ConnectionFactory connectionFactory) {
        return new RabbitAdmin(connectionFactory);
    }

    // ── HTTP ──────────────────────────────────────────────────────────────────

    @Bean
    public RestTemplate restTemplate() {
        // Simple RestTemplate – no custom timeout builder needed for integration tests
        return new org.springframework.web.client.RestTemplate();
    }

    // ── Jackson ───────────────────────────────────────────────────────────────

    @Bean
    public ObjectMapper objectMapper() {
        return new ObjectMapper()
                .registerModule(new JavaTimeModule())
                .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
                .configure(
                    com.fasterxml.jackson.databind.DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES,
                    false);
    }
}



