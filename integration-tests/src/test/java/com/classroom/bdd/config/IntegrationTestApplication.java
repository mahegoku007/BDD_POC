package com.classroom.bdd.config;

import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.ComponentScan;

/**
 * Minimal Spring Boot application used only by the Cucumber integration-test suite.
 *
 * <p>No web server is started. The context provides only infrastructure beans:
 * {@code RestTemplate}, {@code RabbitTemplate}, {@code RabbitAdmin},
 * and {@code ObjectMapper} (defined in {@link TestInfrastructureConfig}).
 *
 * <p>RabbitMQ listener containers are NOT started (configured in
 * {@code application-test.yml}) – tests publish and receive messages manually.
 *
 * <p>Pre-requisite: {@code docker-compose up -d} at the project root must be
 * run before executing the BDD suite.
 */
@SpringBootApplication
@ComponentScan(basePackages = "com.classroom.bdd")
public class IntegrationTestApplication {
    // Intentionally empty
}


