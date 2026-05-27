package com.classroom.booking;

import com.classroom.booking.messaging.BookingProducer;
import com.classroom.booking.service.BookingService;
import com.classroom.common.dto.BookingRequest;
import com.classroom.common.dto.TimeSlot;
import com.classroom.common.enums.BookingStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.time.LocalTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

/**
 * Unit tests for {@link BookingService}.
 * The {@link BookingProducer} is mocked – no RabbitMQ broker is needed.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("BookingService")
class BookingServiceTest {

    @Mock
    private BookingProducer bookingProducer;

    @InjectMocks
    private BookingService bookingService;

    private BookingRequest validRequest;

    @BeforeEach
    void setUp() {
        validRequest = BookingRequest.builder()
                .classroomId("CR-101")
                .date(LocalDate.now().plusDays(1))
                .timeSlot(new TimeSlot(LocalTime.of(9, 0), LocalTime.of(10, 0)))
                .requestedBy("alice@example.com")
                .build();
    }

    // ─────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("assigns a bookingId when none is provided")
    void assignsBookingId() {
        BookingRequest result = bookingService.submitBooking(validRequest);

        assertThat(result.getBookingId())
                .isNotNull()
                .isNotBlank()
                .startsWith("BK-");
    }

    @Test
    @DisplayName("sets status to PENDING")
    void setsStatusToPending() {
        BookingRequest result = bookingService.submitBooking(validRequest);

        assertThat(result.getStatus()).isEqualTo(BookingStatus.PENDING);
    }

    @Test
    @DisplayName("publishes the request exactly once via BookingProducer")
    void publishesExactlyOnce() {
        bookingService.submitBooking(validRequest);

        verify(bookingProducer, times(1)).publishBookingRequest(validRequest);
    }

    @Test
    @DisplayName("published request carries the assigned bookingId")
    void publishedRequestHasBookingId() {
        ArgumentCaptor<BookingRequest> captor = ArgumentCaptor.forClass(BookingRequest.class);

        bookingService.submitBooking(validRequest);
        verify(bookingProducer).publishBookingRequest(captor.capture());

        assertThat(captor.getValue().getBookingId()).startsWith("BK-");
    }

    @Test
    @DisplayName("preserves an existing bookingId if already set")
    void preservesExistingBookingId() {
        validRequest.setBookingId("BK-existing1");

        BookingRequest result = bookingService.submitBooking(validRequest);

        assertThat(result.getBookingId()).isEqualTo("BK-existing1");
    }

    @Test
    @DisplayName("throws IllegalArgumentException when endTime is before startTime")
    void throwsOnInvalidTimeSlot() {
        validRequest.setTimeSlot(new TimeSlot(LocalTime.of(11, 0), LocalTime.of(9, 0)));

        assertThatThrownBy(() -> bookingService.submitBooking(validRequest))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("endTime must be after startTime");
    }

    @Test
    @DisplayName("throws IllegalArgumentException when endTime equals startTime")
    void throwsOnEqualTimes() {
        validRequest.setTimeSlot(new TimeSlot(LocalTime.of(10, 0), LocalTime.of(10, 0)));

        assertThatThrownBy(() -> bookingService.submitBooking(validRequest))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("throws IllegalArgumentException when timeSlot is null")
    void throwsOnNullTimeSlot() {
        validRequest.setTimeSlot(null);

        assertThatThrownBy(() -> bookingService.submitBooking(validRequest))
                .isInstanceOf(IllegalArgumentException.class);
    }
}

