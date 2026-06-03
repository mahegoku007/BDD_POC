package com.classroom.gateway.processor;

import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.springframework.stereotype.Component;

/**
 * Finalizes the batch response by closing the JSON array bracket
 * and setting the HTTP response code to 200.
 */
@Component("batchCompletionProcessor")
public class BatchCompletionProcessor implements Processor {

    @Override
    public void process(Exchange exchange) throws Exception {
        String body = exchange.getIn().getBody(String.class);
        // Close the JSON array
        exchange.getIn().setBody(body + "]");
        exchange.getIn().setHeader(Exchange.HTTP_RESPONSE_CODE, 200);
        exchange.getIn().setHeader(Exchange.CONTENT_TYPE, "application/json");
    }
}

