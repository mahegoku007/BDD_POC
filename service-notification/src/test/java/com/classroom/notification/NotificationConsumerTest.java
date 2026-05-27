package com.classroom.notification;

import com.classroom.common.dto.BookingResponse;
import com.classroom.common.dto.TimeSlot;
import com.classroom.common.enums.BookingStatus;
import com.classroom.notification.messaging.NotificationConsumer;
import com.classroom.notification.service.NotificationService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.time.LocalTime;

import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

/**
 * Unit tests for {@link NotificationConsumer}.
 * {@link NotificationService} is mocked – no Spring context or broker needed.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("NotificationConsumer")
class NotificationConsumerTest {

    @Mock
    private NotificationService notificationService;

    @InjectMocks
    private NotificationConsumer notificationConsumer;

    @Test
    @DisplayName("handleConfirmed() delegates to NotificationService.sendConfirmation()")
    void handleConfirmedDelegates() {
        BookingResponse response = confirmedResponse("BK-001", "alice@example.com");

        notificationConsumer.handleConfirmed(response);

        verify(notificationService, times(1)).sendConfirmation(response);
    }

    @Test
    @DisplayName("handleRejected() delegates to NotificationService.sendRejection()")
    void handleRejectedDelegates() {
        BookingResponse response = rejectedResponse("BK-002", "bob@example.com");

        notificationConsumer.handleRejected(response);

        verify(notificationService, times(1)).sendRejection(response);
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private BookingResponse confirmedResponse(String bookingId, String email) {
        return BookingResponse.builder()
                .bookingId(bookingId)
                .classroomId("CR-101")
                .date(LocalDate.of(2026, 6, 1))
                .timeSlot(new TimeSlot(LocalTime.of(9, 0), LocalTime.of(10, 0)))
                .requestedBy(email)
                .status(BookingStatus.CONFIRMED)
                .message("Your booking " + bookingId + " for CR-101 on 2026-06-01 is CONFIRMED")
                .build();
    }

    private BookingResponse rejectedResponse(String bookingId, String email) {
        return BookingResponse.builder()
                .bookingId(bookingId)
                .classroomId("CR-101")
                .date(LocalDate.of(2026, 6, 1))
                .timeSlot(new TimeSlot(LocalTime.of(9, 0), LocalTime.of(10, 0)))
                .requestedBy(email)
                .status(BookingStatus.REJECTED)
                .message("Your booking request for CR-101 on 2026-06-01 has been REJECTED – Time slot is already booked")
                .build();
    }
}

