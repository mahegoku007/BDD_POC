package com.classroom.bdd.config;

import io.cucumber.spring.CucumberContextConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

/**
 * Wires the Spring application context into Cucumber's dependency-injection container.
 *
 * <p>Every step-definition class that uses {@code @Autowired} receives beans from
 * the context started here. The {@code webEnvironment = NONE} setting means Spring's
 * embedded web server is NOT started – all HTTP calls go to the externally-running
 * microservices.
 *
 * <p><strong>Pre-requisites before running the BDD suite:</strong>
 * <ol>
 *   <li>{@code docker-compose up -d} – starts RabbitMQ on localhost:5672</li>
 *   <li>Start all four microservices (see README / instructions)</li>
 * </ol>
 */
@CucumberContextConfiguration
@SpringBootTest(
        classes = IntegrationTestApplication.class,
        webEnvironment = SpringBootTest.WebEnvironment.NONE)
@ActiveProfiles("test")
public class CucumberSpringConfig {
    // Intentionally empty – presence of this class is enough for Cucumber Spring
}

