package com.classroom.gateway.processor;

import com.classroom.common.dto.BookingRequest;
import com.classroom.common.dto.TimeSlot;
import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.springframework.stereotype.Component;

/**
 * Validates that endTime is after startTime in the booking request's time slot.
 * Throws IllegalArgumentException (→ 400) if validation fails.
 */
@Component("timeSlotValidatorProcessor")
public class TimeSlotValidatorProcessor implements Processor {

    @Override
    public void process(Exchange exchange) throws Exception {
        BookingRequest req = exchange.getIn().getBody(BookingRequest.class);
        if (req == null || req.getTimeSlot() == null) {
            return; // Already handled by previous null checks
        }
        TimeSlot slot = req.getTimeSlot();
        if (slot.getStartTime() != null && slot.getEndTime() != null) {
            if (!slot.getEndTime().isAfter(slot.getStartTime())) {
                throw new IllegalArgumentException("endTime must be after startTime");
            }
        }
    }
}

