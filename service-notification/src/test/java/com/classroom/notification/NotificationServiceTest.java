package com.classroom.notification;

import com.classroom.common.dto.BookingResponse;
import com.classroom.common.dto.TimeSlot;
import com.classroom.common.enums.BookingStatus;
import com.classroom.notification.model.NotificationRecord;
import com.classroom.notification.model.NotificationType;
import com.classroom.notification.service.NotificationLog;
import com.classroom.notification.service.NotificationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.LocalTime;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for {@link NotificationService} and {@link NotificationLog}.
 * No Spring context or RabbitMQ broker needed.
 */
@DisplayName("NotificationService")
class NotificationServiceTest {

    private NotificationLog notificationLog;
    private NotificationService notificationService;

    @BeforeEach
    void setUp() {
        notificationLog = new NotificationLog();
        notificationService = new NotificationService(notificationLog);
    }

    // ── sendConfirmation ──────────────────────────────────────────────────────

    @Nested
    @DisplayName("sendConfirmation()")
    class SendConfirmation {

        @Test
        @DisplayName("records a CONFIRMATION entry in the log")
        void addsConfirmationToLog() {
            notificationService.sendConfirmation(confirmedResponse("BK-001", "alice@example.com"));

            assertThat(notificationLog.count()).isEqualTo(1);
            assertThat(notificationLog.hasConfirmationFor("alice@example.com")).isTrue();
        }

        @Test
        @DisplayName("notification message contains bookingId, classroomId, date and CONFIRMED")
        void confirmationMessageContent() {
            notificationService.sendConfirmation(confirmedResponse("BK-001", "alice@example.com"));

            NotificationRecord record = notificationLog.findByRecipient("alice@example.com").get(0);
            assertThat(record.getMessage())
                    .contains("BK-001")
                    .contains("CR-101")
                    .contains("2026-06-01")
                    .containsIgnoringCase("CONFIRMED");
        }

        @Test
        @DisplayName("notification type is CONFIRMATION")
        void notificationTypeIsConfirmation() {
            notificationService.sendConfirmation(confirmedResponse("BK-001", "alice@example.com"));

            assertThat(notificationLog.findByType(NotificationType.CONFIRMATION)).hasSize(1);
            assertThat(notificationLog.findByType(NotificationType.REJECTION)).isEmpty();
        }
    }

    // ── sendRejection ─────────────────────────────────────────────────────────

    @Nested
    @DisplayName("sendRejection()")
    class SendRejection {

        @Test
        @DisplayName("records a REJECTION entry in the log")
        void addsRejectionToLog() {
            notificationService.sendRejection(rejectedResponse("BK-002", "bob@example.com"));

            assertThat(notificationLog.count()).isEqualTo(1);
            assertThat(notificationLog.hasRejectionFor("bob@example.com")).isTrue();
        }

        @Test
        @DisplayName("notification message contains rejection reason")
        void rejectionMessageContainsReason() {
            notificationService.sendRejection(rejectedResponse("BK-002", "bob@example.com"));

            NotificationRecord record = notificationLog.findByRecipient("bob@example.com").get(0);
            assertThat(record.getMessage()).contains("Time slot is already booked");
        }

        @Test
        @DisplayName("notification type is REJECTION")
        void notificationTypeIsRejection() {
            notificationService.sendRejection(rejectedResponse("BK-002", "bob@example.com"));

            assertThat(notificationLog.findByType(NotificationType.REJECTION)).hasSize(1);
            assertThat(notificationLog.findByType(NotificationType.CONFIRMATION)).isEmpty();
        }
    }

    // ── Idempotency ───────────────────────────────────────────────────────────

    @Nested
    @DisplayName("Idempotency")
    class Idempotency {

        @Test
        @DisplayName("duplicate confirmation event is suppressed – only 1 record stored")
        void duplicateConfirmationSuppressed() {
            BookingResponse response = confirmedResponse("BK-DUPE", "carol@example.com");

            notificationService.sendConfirmation(response);
            notificationService.sendConfirmation(response); // second call = duplicate

            assertThat(notificationLog.findByBookingId("BK-DUPE")).hasSize(1);
        }

        @Test
        @DisplayName("duplicate rejection event is suppressed")
        void duplicateRejectionSuppressed() {
            BookingResponse response = rejectedResponse("BK-DUPE2", "dave@example.com");

            notificationService.sendRejection(response);
            notificationService.sendRejection(response);

            assertThat(notificationLog.findByBookingId("BK-DUPE2")).hasSize(1);
        }
    }

    // ── NotificationLog queries ───────────────────────────────────────────────

    @Nested
    @DisplayName("NotificationLog queries")
    class NotificationLogQueries {

        @Test
        @DisplayName("findByRecipient is case-insensitive")
        void findByRecipientCaseInsensitive() {
            notificationService.sendConfirmation(confirmedResponse("BK-CI", "Alice@Example.COM"));

            assertThat(notificationLog.findByRecipient("alice@example.com")).hasSize(1);
        }

        @Test
        @DisplayName("clear() removes all records")
        void clearRemovesAll() {
            notificationService.sendConfirmation(confirmedResponse("BK-A", "a@x.com"));
            notificationService.sendRejection(rejectedResponse("BK-B", "b@x.com"));

            notificationLog.clear();

            assertThat(notificationLog.count()).isEqualTo(0);
        }

        @Test
        @DisplayName("multiple recipients tracked independently")
        void multipleRecipients() {
            notificationService.sendConfirmation(confirmedResponse("BK-C1", "user1@x.com"));
            notificationService.sendRejection(rejectedResponse("BK-C2", "user2@x.com"));
            notificationService.sendConfirmation(confirmedResponse("BK-C3", "user3@x.com"));

            assertThat(notificationLog.count()).isEqualTo(3);
            assertThat(notificationLog.findByRecipient("user1@x.com")).hasSize(1);
            assertThat(notificationLog.findByRecipient("user2@x.com")).hasSize(1);
            assertThat(notificationLog.findByType(NotificationType.CONFIRMATION)).hasSize(2);
            assertThat(notificationLog.findByType(NotificationType.REJECTION)).hasSize(1);
        }

        @Test
        @DisplayName("anyMessageContains finds text across all records")
        void anyMessageContains() {
            notificationService.sendConfirmation(confirmedResponse("BK-D", "d@x.com"));

            assertThat(notificationLog.anyMessageContains("CR-101")).isTrue();
            assertThat(notificationLog.anyMessageContains("CR-999")).isFalse();
        }
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
                .message(String.format("Your booking %s for CR-101 on 2026-06-01 (09:00 - 10:00) is CONFIRMED", bookingId))
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

