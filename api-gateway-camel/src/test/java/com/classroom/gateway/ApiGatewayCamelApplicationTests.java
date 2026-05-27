package com.classroom.gateway;

import org.apache.camel.test.spring.junit5.CamelSpringBootTest;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

/**
 * Smoke test – verifies the full Spring + Camel context loads without errors.
 */
@CamelSpringBootTest
@SpringBootTest
@ActiveProfiles("test")
class ApiGatewayCamelApplicationTests {

    @Test
    void contextLoads() {
        // passes if all Camel routes and Spring beans initialise without error
    }
}

