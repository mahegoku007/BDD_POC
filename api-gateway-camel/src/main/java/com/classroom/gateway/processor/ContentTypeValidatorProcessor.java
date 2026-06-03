package com.classroom.gateway.processor;

import com.classroom.gateway.exception.UnsupportedMediaTypeException;
import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.springframework.stereotype.Component;

/**
 * Validates that the incoming request has Content-Type: application/json.
 * Throws {@link UnsupportedMediaTypeException} (→ 415) if not.
 */
@Component("contentTypeValidatorProcessor")
public class ContentTypeValidatorProcessor implements Processor {

    @Override
    public void process(Exchange exchange) throws Exception {
        String ct = exchange.getIn().getHeader(Exchange.CONTENT_TYPE, String.class);
        if (ct == null || !ct.contains("application/json")) {
            throw new UnsupportedMediaTypeException(
                    "Content-Type must be application/json, got: " + ct);
        }
    }
}

