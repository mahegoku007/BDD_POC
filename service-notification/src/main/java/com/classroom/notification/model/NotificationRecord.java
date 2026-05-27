package com.classroom.notification.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * Immutable record of a single notification that was dispatched.
 *
 * <p>Stored in {@code NotificationLog}
 * so that:
 * <ul>
 *   <li>Application logs can be searched without a database</li>
 *   <li>Cucumber / integration-test step definitions can assert
 *       exactly which notifications were sent and to whom</li>
 * </ul>
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class NotificationRecord {

    /** Booking identifier this notification relates to. */
    private String bookingId;

    /** E-mail address the notification was sent to. */
    private String recipientEmail;

    /** Whether this was a confirmation or rejection. */
    private NotificationType type;

    /** Full notification message text. */
    private String message;

    /** UTC timestamp at which the notification was dispatched. */
    @Builder.Default
    private LocalDateTime sentAt = LocalDateTime.now();
}

