package com.classroom.notification.service;

import com.classroom.notification.model.NotificationRecord;
import com.classroom.notification.model.NotificationType;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.stream.Collectors;

/**
 * Thread-safe in-memory store of all notifications dispatched during
 * the lifetime of this service instance.
 *
 * <p>Purpose:
 * <ul>
 *   <li>Provides a queryable audit trail without a database</li>
 *   <li>Allows Cucumber BDD step definitions to assert sent notifications
 *       by recipient, booking ID, type, or message content</li>
 *   <li>Supports idempotency checks (de-duplicate by bookingId)</li>
 * </ul>
 *
 * <p>{@link CopyOnWriteArrayList} is used so reads never block and
 * concurrent consumer threads don't corrupt the list.
 */
@Component
public class NotificationLog {

    private final CopyOnWriteArrayList<NotificationRecord> records = new CopyOnWriteArrayList<>();

    // ── Write ─────────────────────────────────────────────────────────────────

    /**
     * Adds a new notification record to the log if one with the same
     * {@code bookingId} and {@code type} has not already been recorded
     * (idempotency guard).
     *
     * @param record the notification to record
     * @return {@code true} if the record was added;
     *         {@code false} if it was a duplicate
     */
    public boolean add(NotificationRecord record) {
        boolean alreadyExists = records.stream()
                .anyMatch(r -> r.getBookingId().equals(record.getBookingId())
                               && r.getType() == record.getType());

        if (alreadyExists) {
            return false;
        }
        records.add(record);
        return true;
    }

    /**
     * Clears all records. Intended for use in test {@code @BeforeEach} hooks.
     */
    public void clear() {
        records.clear();
    }

    // ── Query ─────────────────────────────────────────────────────────────────

    /**
     * Returns all notification records (unmodifiable snapshot).
     */
    public List<NotificationRecord> getAll() {
        return List.copyOf(records);
    }

    /**
     * Returns the total number of notifications sent.
     */
    public int count() {
        return records.size();
    }

    /**
     * Returns all notifications sent to a specific e-mail address.
     *
     * @param email recipient e-mail (case-insensitive)
     */
    public List<NotificationRecord> findByRecipient(String email) {
        return records.stream()
                .filter(r -> r.getRecipientEmail().equalsIgnoreCase(email))
                .collect(Collectors.toList());
    }

    /**
     * Returns all notifications for a specific booking ID.
     *
     * @param bookingId the booking identifier
     */
    public List<NotificationRecord> findByBookingId(String bookingId) {
        return records.stream()
                .filter(r -> r.getBookingId().equals(bookingId))
                .collect(Collectors.toList());
    }

    /**
     * Returns all notifications of a specific type.
     *
     * @param type {@link NotificationType#CONFIRMATION} or {@link NotificationType#REJECTION}
     */
    public List<NotificationRecord> findByType(NotificationType type) {
        return records.stream()
                .filter(r -> r.getType() == type)
                .collect(Collectors.toList());
    }

    /**
     * Returns {@code true} if at least one confirmation was sent to the given e-mail.
     *
     * @param email recipient e-mail (case-insensitive)
     */
    public boolean hasConfirmationFor(String email) {
        return findByRecipient(email).stream()
                .anyMatch(r -> r.getType() == NotificationType.CONFIRMATION);
    }

    /**
     * Returns {@code true} if at least one rejection was sent to the given e-mail.
     *
     * @param email recipient e-mail (case-insensitive)
     */
    public boolean hasRejectionFor(String email) {
        return findByRecipient(email).stream()
                .anyMatch(r -> r.getType() == NotificationType.REJECTION);
    }

    /**
     * Returns {@code true} if any notification text contains the given substring.
     *
     * @param substring text to search for (case-sensitive)
     */
    public boolean anyMessageContains(String substring) {
        return records.stream()
                .anyMatch(r -> r.getMessage().contains(substring));
    }

    /**
     * Returns notifications for {@code email} whose message contains {@code substring}.
     *
     * @param email     recipient e-mail (case-insensitive)
     * @param substring text to search for (case-sensitive)
     */
    public List<NotificationRecord> findByRecipientAndMessageContaining(String email,
                                                                         String substring) {
        return findByRecipient(email).stream()
                .filter(r -> r.getMessage().contains(substring))
                .collect(Collectors.toList());
    }
}

