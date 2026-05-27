package com.classroom.gateway.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

/**
 * Jackson configuration for the API Gateway.
 *
 * <p>Registers the {@link JavaTimeModule} so that {@code LocalDate},
 * {@code LocalTime} and {@code LocalDateTime} fields in
 * {@link com.classroom.common.dto.BookingRequest} and
 * {@link com.classroom.common.dto.BookingResponse} are correctly serialised
 * to/from ISO-8601 strings rather than numeric timestamps.
 *
 * <p>This bean is used by:
 * <ul>
 *   <li>Spring MVC (response bodies for any error endpoints)</li>
 *   <li>Apache Camel's JSON binding mode (REST DSL auto-unmarshal)</li>
 * </ul>
 */
@Configuration
public class JacksonConfig {

    @Bean
    @Primary
    public ObjectMapper objectMapper() {
        return new ObjectMapper()
                .registerModule(new JavaTimeModule())
                // Dates as "2026-06-01" / "09:00", not epoch-millis
                .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
                // Don't fail on unknown JSON fields (forward-compatibility)
                .configure(
                        com.fasterxml.jackson.databind.DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES,
                        false);
    }
}

