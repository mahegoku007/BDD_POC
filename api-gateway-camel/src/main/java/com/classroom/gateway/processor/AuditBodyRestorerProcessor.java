package com.classroom.gateway.processor;

import com.classroom.common.dto.BookingRequest;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.springframework.stereotype.Component;

/**
 * Restores the BookingRequest body from the auditPayload header
 * after the audit HTTP call replaces the body with the audit response.
 * This ensures the next step in the route slip gets the original BookingRequest.
 */
@Component("auditBodyRestorerProcessor")
@RequiredArgsConstructor
public class AuditBodyRestorerProcessor implements Processor {

    private final ObjectMapper objectMapper;

    @Override
    public void process(Exchange exchange) throws Exception {
        String payload = exchange.getIn().getHeader("auditPayload", String.class);
        if (payload != null) {
            BookingRequest req = objectMapper.readValue(payload, BookingRequest.class);
            exchange.getIn().setBody(req);
        }
    }
}

