package com.classroom.gateway.processor;

import org.apache.camel.AggregationStrategy;
import org.apache.camel.Exchange;
import org.springframework.stereotype.Component;

/**
 * Aggregation strategy for batch booking requests.
 * Collects individual booking responses into a JSON array string.
 */
@Component("batchAggregationStrategy")
public class BatchBookingAggregationStrategy implements AggregationStrategy {

    @Override
    public Exchange aggregate(Exchange oldExchange, Exchange newExchange) {
        String newBody = newExchange.getIn().getBody(String.class);

        if (oldExchange == null) {
            // First message — start the JSON array
            newExchange.getIn().setBody("[" + newBody);
            return newExchange;
        }

        // Append to the existing aggregated body
        String existing = oldExchange.getIn().getBody(String.class);
        oldExchange.getIn().setBody(existing + "," + newBody);
        return oldExchange;
    }
}

