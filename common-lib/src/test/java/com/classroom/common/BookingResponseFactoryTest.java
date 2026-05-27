package com.classroom.common;

import com.classroom.common.dto.BookingRequest;
import com.classroom.common.dto.BookingResponse;
import com.classroom.common.dto.TimeSlot;
import com.classroom.common.enums.BookingStatus;
import com.classroom.common.util.BookingResponseFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.LocalTime;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for {@link BookingResponseFactory}.
 */
@DisplayName("BookingResponseFactory")
class BookingResponseFactoryTest {

    private BookingRequest request;

    @BeforeEach
    void setUp() {
        request = BookingRequest.builder()
                .bookingId("BK-test01")
                .classroomId("CR-101")
                .date(LocalDate.of(2026, 6, 1))
                .timeSlot(new TimeSlot(LocalTime.of(9, 0), LocalTime.of(10, 0)))
                .requestedBy("alice@example.com")
                .status(BookingStatus.PENDING)
                .build();
    }

    @Test
    @DisplayName("confirmed() sets status to CONFIRMED and copies all fields")
    void confirmedResponse() {
        BookingResponse response = BookingResponseFactory.confirmed(request);

        assertThat(response.getStatus()).isEqualTo(BookingStatus.CONFIRMED);
        assertThat(response.getBookingId()).isEqualTo("BK-test01");
        assertThat(response.getClassroomId()).isEqualTo("CR-101");
        assertThat(response.getDate()).isEqualTo(LocalDate.of(2026, 6, 1));
        assertThat(response.getRequestedBy()).isEqualTo("alice@example.com");
        assertThat(response.getMessage()).containsIgnoringCase("CONFIRMED");
        assertThat(response.getTimestamp()).isNotNull();
    }

    @Test
    @DisplayName("rejected() sets status to REJECTED and embeds reason in message")
    void rejectedResponse() {
        BookingResponse response = BookingResponseFactory.rejected(request, "Time slot is already booked");

        assertThat(response.getStatus()).isEqualTo(BookingStatus.REJECTED);
        assertThat(response.getBookingId()).isEqualTo("BK-test01");
        assertThat(response.getMessage()).containsIgnoringCase("REJECTED");
        assertThat(response.getMessage()).contains("Time slot is already booked");
    }

    @Test
    @DisplayName("confirmed() produces a defensive copy of the TimeSlot")
    void confirmedCopiesTimeSlot() {
        BookingResponse response = BookingResponseFactory.confirmed(request);

        // Mutating the original should not affect the response
        request.getTimeSlot().setStartTime(LocalTime.of(11, 0));
        assertThat(response.getTimeSlot().getStartTime()).isEqualTo(LocalTime.of(9, 0));
    }
}

