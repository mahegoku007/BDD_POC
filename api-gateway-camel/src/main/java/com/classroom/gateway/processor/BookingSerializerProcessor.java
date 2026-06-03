package com.classroom.gateway.processor;

import com.classroom.common.dto.BookingRequest;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.springframework.stereotype.Component;

/**
 * Serializes the {@link BookingRequest} POJO to a JSON string for HTTP forwarding.
 * Uses the Spring-managed ObjectMapper with JavaTimeModule.
 */
@Component("bookingSerializerProcessor")
@RequiredArgsConstructor
public class BookingSerializerProcessor implements Processor {

    private final ObjectMapper objectMapper;

    @Override
    public void process(Exchange exchange) throws Exception {
        BookingRequest req = exchange.getIn().getBody(BookingRequest.class);
        exchange.getIn().setBody(objectMapper.writeValueAsString(req));
    }
}

