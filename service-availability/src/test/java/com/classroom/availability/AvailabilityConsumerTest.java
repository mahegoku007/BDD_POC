package com.classroom.availability;

import com.classroom.availability.messaging.AvailabilityConsumer;
import com.classroom.availability.messaging.AvailabilityProducer;
import com.classroom.availability.service.AvailabilityService;
import com.classroom.common.dto.AvailabilityCheckResponse;
import com.classroom.common.dto.BookingRequest;
import com.classroom.common.dto.BookingResponse;
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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link AvailabilityConsumer}.
 * All dependencies are mocked – no broker or database required.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("AvailabilityConsumer")
class AvailabilityConsumerTest {

    @Mock
    private AvailabilityService availabilityService;

    @Mock
    private AvailabilityProducer availabilityProducer;

    @InjectMocks
    private AvailabilityConsumer availabilityConsumer;

    private BookingRequest bookingRequest;

    @BeforeEach
    void setUp() {
        bookingRequest = BookingRequest.builder()
                .bookingId("BK-abc12345")
                .classroomId("CR-101")
                .date(LocalDate.of(2026, 6, 1))
                .timeSlot(new TimeSlot(LocalTime.of(9, 0), LocalTime.of(10, 0)))
                .requestedBy("alice@example.com")
                .status(BookingStatus.PENDING)
                .build();
    }

    @Test
    @DisplayName("when slot is AVAILABLE: confirms booking and publishes confirmed event")
    void availableSlotConfirmsAndPublishes() {
        when(availabilityService.checkAvailability(bookingRequest))
                .thenReturn(AvailabilityCheckResponse.available(
                        bookingRequest.getBookingId(),
                        bookingRequest.getClassroomId(),
                        bookingRequest.getDate(),
                        bookingRequest.getTimeSlot()));

        availabilityConsumer.handleBookingRequest(bookingRequest);

        verify(availabilityService).confirmBooking(bookingRequest);
        verify(availabilityProducer).publishConfirmed(any(BookingResponse.class));
        verify(availabilityProducer, never()).publishRejected(any());
    }

    @Test
    @DisplayName("when slot is AVAILABLE: confirmed response carries CONFIRMED status")
    void confirmedResponseHasCorrectStatus() {
        when(availabilityService.checkAvailability(bookingRequest))
                .thenReturn(AvailabilityCheckResponse.available(
                        bookingRequest.getBookingId(),
                        bookingRequest.getClassroomId(),
                        bookingRequest.getDate(),
                        bookingRequest.getTimeSlot()));

        availabilityConsumer.handleBookingRequest(bookingRequest);

        ArgumentCaptor<BookingResponse> captor = ArgumentCaptor.forClass(BookingResponse.class);
        verify(availabilityProducer).publishConfirmed(captor.capture());

        assertThat(captor.getValue().getStatus()).isEqualTo(BookingStatus.CONFIRMED);
        assertThat(captor.getValue().getBookingId()).isEqualTo("BK-abc12345");
        assertThat(captor.getValue().getMessage()).containsIgnoringCase("CONFIRMED");
    }

    @Test
    @DisplayName("when slot is UNAVAILABLE: rejects booking and publishes rejected event")
    void unavailableSlotRejectsAndPublishes() {
        when(availabilityService.checkAvailability(bookingRequest))
                .thenReturn(AvailabilityCheckResponse.unavailable(
                        bookingRequest.getBookingId(),
                        bookingRequest.getClassroomId(),
                        bookingRequest.getDate(),
                        bookingRequest.getTimeSlot(),
                        AvailabilityService.CONFLICT_REASON));

        availabilityConsumer.handleBookingRequest(bookingRequest);

        verify(availabilityProducer).publishRejected(any(BookingResponse.class));
        verify(availabilityProducer, never()).publishConfirmed(any());
        verify(availabilityService, never()).confirmBooking(any());
    }

    @Test
    @DisplayName("when slot is UNAVAILABLE: rejected response carries REJECTED status and reason")
    void rejectedResponseHasCorrectStatusAndReason() {
        when(availabilityService.checkAvailability(bookingRequest))
                .thenReturn(AvailabilityCheckResponse.unavailable(
                        bookingRequest.getBookingId(),
                        bookingRequest.getClassroomId(),
                        bookingRequest.getDate(),
                        bookingRequest.getTimeSlot(),
                        AvailabilityService.CONFLICT_REASON));

        availabilityConsumer.handleBookingRequest(bookingRequest);

        ArgumentCaptor<BookingResponse> captor = ArgumentCaptor.forClass(BookingResponse.class);
        verify(availabilityProducer).publishRejected(captor.capture());

        assertThat(captor.getValue().getStatus()).isEqualTo(BookingStatus.REJECTED);
        assertThat(captor.getValue().getMessage()).contains(AvailabilityService.CONFLICT_REASON);
    }
}

