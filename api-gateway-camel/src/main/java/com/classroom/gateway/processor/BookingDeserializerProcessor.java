package com.classroom.gateway.processor;

import com.classroom.common.dto.BookingRequest;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.springframework.stereotype.Component;

/**
 * Deserializes the raw JSON request body into a {@link BookingRequest} POJO
 * using the Spring-managed ObjectMapper (which has JavaTimeModule registered).
 * Throws {@link IllegalArgumentException} (→ 400) on parse failures.
 */
@Component("bookingDeserializerProcessor")
@RequiredArgsConstructor
public class BookingDeserializerProcessor implements Processor {

    private final ObjectMapper objectMapper;

    @Override
    public void process(Exchange exchange) throws Exception {
        String json = exchange.getIn().getBody(String.class);
        try {
            BookingRequest req = objectMapper.readValue(json, BookingRequest.class);
            exchange.getIn().setBody(req);
        } catch (Exception e) {
            throw new IllegalArgumentException("Invalid request body: " + e.getMessage(), e);
        }
    }
}

