package com.classroom.notification.controller;

import com.classroom.notification.model.NotificationRecord;
import com.classroom.notification.model.NotificationType;
import com.classroom.notification.service.NotificationLog;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * Test-support REST endpoints for the Notification Service.
 *
 * <p>Exposes the in-memory {@link NotificationLog} over HTTP so that
 * Cucumber BDD step definitions can assert notification outcomes without
 * needing direct bean access.
 */
@Slf4j
@RestController
@RequestMapping("/test")
@RequiredArgsConstructor
public class NotificationTestController {

    private final NotificationLog notificationLog;

    /**
     * Returns all notification records (unfiltered).
     */
    @GetMapping("/notifications")
    public ResponseEntity<List<NotificationRecord>> getAllNotifications() {
        return ResponseEntity.ok(notificationLog.getAll());
    }

    /**
     * Returns notifications for a specific recipient e-mail.
     *
     * @param recipient e-mail address (case-insensitive)
     */
    @GetMapping("/notifications/by-recipient")
    public ResponseEntity<List<NotificationRecord>> getByRecipient(
            @RequestParam String recipient) {
        return ResponseEntity.ok(notificationLog.findByRecipient(recipient));
    }

    /**
     * Returns notifications for a specific booking ID.
     */
    @GetMapping("/notifications/by-booking")
    public ResponseEntity<List<NotificationRecord>> getByBookingId(
            @RequestParam String bookingId) {
        return ResponseEntity.ok(notificationLog.findByBookingId(bookingId));
    }

    /**
     * Returns a summary count per notification type.
     */
    @GetMapping("/notifications/summary")
    public ResponseEntity<Map<String, Object>> getSummary() {
        return ResponseEntity.ok(Map.of(
                "total",         notificationLog.count(),
                "confirmations", notificationLog.findByType(NotificationType.CONFIRMATION).size(),
                "rejections",    notificationLog.findByType(NotificationType.REJECTION).size()
        ));
    }

    /**
     * Clears all notification records.
     * Called between Cucumber scenarios to reset state.
     */
    @DeleteMapping("/notifications")
    public ResponseEntity<Void> clearAll() {
        int before = notificationLog.count();
        notificationLog.clear();
        log.info("[TEST-CTRL] Cleared {} notification record(s)", before);
        return ResponseEntity.noContent().build();
    }
}

