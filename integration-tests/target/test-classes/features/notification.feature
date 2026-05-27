@notification
Feature: Booking Notification
  As a user
  I want to receive a notification after my booking attempt
  So that I know whether my classroom was successfully reserved or rejected

  Background:
    Given the notification service is running
    And the notification log is empty

  # ─────────────────────────────────────────────
  # Confirmation Notifications
  # ─────────────────────────────────────────────

  @smoke @confirmation
  Scenario: Confirmation notification sent on booking confirmed event
    Given a "booking.confirmed" event is published with:
      | field       | value              |
      | bookingId   | BK-0001            |
      | classroomId | CR-101             |
      | date        | 2026-06-01         |
      | startTime   | 09:00              |
      | endTime     | 10:00              |
      | requestedBy | alice@example.com  |
    When the notification service processes the event
    Then a confirmation notification should be logged for "alice@example.com"
    And the notification message should contain "Your booking BK-0001 for CR-101 on 2026-06-01 (09:00 - 10:00) is CONFIRMED"

  @confirmation
  Scenario: Confirmation notification contains correct classroom and time details
    Given a "booking.confirmed" event is published with:
      | field       | value             |
      | bookingId   | BK-0002           |
      | classroomId | CR-103            |
      | date        | 2026-07-15        |
      | startTime   | 14:00             |
      | endTime     | 16:00             |
      | requestedBy | bob@example.com   |
    When the notification service processes the event
    Then a confirmation notification should be logged for "bob@example.com"
    And the notification message should contain "CR-103"
    And the notification message should contain "14:00 - 16:00"
    And the notification message should contain "2026-07-15"

  # ─────────────────────────────────────────────
  # Rejection Notifications
  # ─────────────────────────────────────────────

  @smoke @rejection
  Scenario: Rejection notification sent on booking rejected event
    Given a "booking.rejected" event is published with:
      | field       | value              |
      | bookingId   | BK-0003            |
      | classroomId | CR-102             |
      | date        | 2026-06-02         |
      | startTime   | 11:00              |
      | endTime     | 12:00              |
      | requestedBy | carol@example.com  |
      | reason      | Time slot is already booked |
    When the notification service processes the event
    Then a rejection notification should be logged for "carol@example.com"
    And the notification message should contain "Your booking request for CR-102 on 2026-06-02 has been REJECTED"
    And the notification message should contain "Time slot is already booked"

  @rejection
  Scenario: Rejection notification includes the reason for rejection
    Given a "booking.rejected" event is published with:
      | field       | value              |
      | bookingId   | BK-0004            |
      | classroomId | CR-101             |
      | date        | 2026-06-05         |
      | startTime   | 09:00              |
      | endTime     | 10:00              |
      | requestedBy | dave@example.com   |
      | reason      | Time slot is already booked |
    When the notification service processes the event
    Then a rejection notification should be logged for "dave@example.com"
    And the notification message should contain "Time slot is already booked"

  # ─────────────────────────────────────────────
  # Multiple Notifications
  # ─────────────────────────────────────────────

  @multi-notification
  Scenario: Multiple notifications sent for multiple events in sequence
    Given the following booking events are published:
      | eventType          | bookingId | classroomId | requestedBy           |
      | booking.confirmed  | BK-0010   | CR-101      | user1@example.com     |
      | booking.rejected   | BK-0011   | CR-102      | user2@example.com     |
      | booking.confirmed  | BK-0012   | CR-103      | user3@example.com     |
    When the notification service processes all events
    Then "3" notifications should have been sent
    And a confirmation notification should be logged for "user1@example.com"
    And a rejection notification should be logged for "user2@example.com"
    And a confirmation notification should be logged for "user3@example.com"

  # ─────────────────────────────────────────────
  # Idempotency
  # ─────────────────────────────────────────────

  @idempotency
  Scenario: Duplicate event does not produce duplicate notification
    Given a "booking.confirmed" event is published with:
      | field       | value              |
      | bookingId   | BK-0020            |
      | classroomId | CR-101             |
      | date        | 2026-06-10         |
      | startTime   | 10:00              |
      | endTime     | 11:00              |
      | requestedBy | eve@example.com    |
    And the same event is published again
    When the notification service processes both events
    Then only "1" notification should have been sent for "BK-0020"

