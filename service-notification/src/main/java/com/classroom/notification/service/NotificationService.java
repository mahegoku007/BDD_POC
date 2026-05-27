package com.classroom.notification.service;

import com.classroom.common.dto.BookingResponse;
import com.classroom.notification.model.NotificationRecord;
import com.classroom.notification.model.NotificationType;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * Dispatches notifications to users after a booking is confirmed or rejected.
 *
 * <p>In this implementation notifications are written to the application log
 * (simulating an e-mail / SMS gateway). Every dispatched notification is also
 * appended to the {@link NotificationLog} so tests and audit queries can
 * inspect what was sent without a real mail server.
 *
 * <p>Replace or extend the {@code dispatch*} private methods to integrate
 * with a real e-mail provider (e.g. Spring Mail, SendGrid, Twilio).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class NotificationService {

    private final NotificationLog notificationLog;

    // ── Public API ────────────────────────────────────────────────────────────

    /**
     * Sends a confirmation notification for a successfully confirmed booking.
     *
     * @param response the confirmed booking response from the availability service
     */
    public void sendConfirmation(BookingResponse response) {
        String message = buildConfirmationMessage(response);

        dispatchNotification(response, NotificationType.CONFIRMATION, message);
    }

    /**
     * Sends a rejection notification for a booking that could not be confirmed.
     *
     * @param response the rejected booking response from the availability service
     */
    public void sendRejection(BookingResponse response) {
        String message = buildRejectionMessage(response);

        dispatchNotification(response, NotificationType.REJECTION, message);
    }

    // ── Private helpers ───────────────────────────────────────────────────────

    /**
     * Core dispatch method: logs the notification and records it in
     * {@link NotificationLog}.
     *
     * @param response         the booking response carrying recipient and booking info
     * @param type             CONFIRMATION or REJECTION
     * @param message          the full notification text
     */
    private void dispatchNotification(BookingResponse response,
                                      NotificationType type,
                                      String message) {

        // ── Simulate notification send (log as INFO) ──────────────────────────
        log.info("[NOTIFICATION-SERVICE] Sending {} notification | bookingId={} | to={} | message={}",
                type,
                response.getBookingId(),
                response.getRequestedBy(),
                message);

        // ── Record in audit log ───────────────────────────────────────────────
        NotificationRecord record = NotificationRecord.builder()
                .bookingId(response.getBookingId())
                .recipientEmail(response.getRequestedBy())
                .type(type)
                .message(message)
                .build();

        boolean added = notificationLog.add(record);

        if (!added) {
            // Idempotency: duplicate event received – skip silently
            log.warn("[NOTIFICATION-SERVICE] Duplicate notification suppressed | bookingId={} | type={}",
                    response.getBookingId(), type);
        } else {
            log.debug("[NOTIFICATION-SERVICE] Notification recorded | bookingId={} | type={}",
                    response.getBookingId(), type);
        }
    }

    /**
     * Builds the confirmation message text.
     * Format: {@code "Your booking BK-xxxx for CR-101 on 2026-06-01 (09:00 - 10:00) is CONFIRMED"}
     */
    private String buildConfirmationMessage(BookingResponse response) {
        return String.format(
                "Your booking %s for %s on %s (%s) is CONFIRMED",
                response.getBookingId(),
                response.getClassroomId(),
                response.getDate(),
                response.getTimeSlot());
    }

    /**
     * Builds the rejection message text.
     * Format: {@code "Your booking request for CR-101 on 2026-06-01 has been REJECTED – <reason>"}
     */
    private String buildRejectionMessage(BookingResponse response) {
        // The availability service already set a descriptive message on the response;
        // use it directly so the reason is preserved verbatim.
        return response.getMessage() != null
                ? response.getMessage()
                : String.format(
                        "Your booking request for %s on %s has been REJECTED",
                        response.getClassroomId(),
                        response.getDate());
    }
}

