@booking
Feature: Classroom Booking
  As a user
  I want to book a classroom for a specific date and time slot
  So that I can conduct my sessions without scheduling conflicts

  Background:
    Given the booking system is up and running
    And the following classrooms exist:
      | classroomId | name        | capacity |
      | CR-101      | Room Alpha  | 30       |
      | CR-102      | Room Beta   | 20       |
      | CR-103      | Room Gamma  | 50       |

  # ─────────────────────────────────────────────
  # Happy Path
  # ─────────────────────────────────────────────

  @smoke @happy-path
  Scenario: Successful booking when classroom is available
    Given classroom "CR-101" is available on "2026-06-01" from "09:00" to "10:00"
    When user "alice@example.com" books classroom "CR-101" on "2026-06-01" from "09:00" to "10:00"
    Then the booking status should be "CONFIRMED"
    And a confirmation notification should be sent to "alice@example.com"
    And the event "booking.confirmed" should be published to RabbitMQ

  @smoke @happy-path
  Scenario: Successful booking for a different time slot on the same day
    Given classroom "CR-102" has an existing booking on "2026-06-02" from "08:00" to "09:00"
    And classroom "CR-102" is available on "2026-06-02" from "10:00" to "11:00"
    When user "bob@example.com" books classroom "CR-102" on "2026-06-02" from "10:00" to "11:00"
    Then the booking status should be "CONFIRMED"
    And a confirmation notification should be sent to "bob@example.com"

  @happy-path
  Scenario: Successful booking for a different classroom at the same time
    Given classroom "CR-101" has an existing booking on "2026-06-03" from "14:00" to "15:00"
    And classroom "CR-102" is available on "2026-06-03" from "14:00" to "15:00"
    When user "carol@example.com" books classroom "CR-102" on "2026-06-03" from "14:00" to "15:00"
    Then the booking status should be "CONFIRMED"
    And a confirmation notification should be sent to "carol@example.com"

  # ─────────────────────────────────────────────
  # Conflict / Rejection Scenarios
  # ─────────────────────────────────────────────

  @smoke @conflict
  Scenario: Booking rejected when classroom is already booked at the exact same time
    Given classroom "CR-101" has an existing booking on "2026-06-04" from "11:00" to "12:00"
    When user "dave@example.com" tries to book classroom "CR-101" on "2026-06-04" from "11:00" to "12:00"
    Then the booking status should be "REJECTED"
    And the rejection reason should be "Time slot is already booked"
    And a rejection notification should be sent to "dave@example.com"
    And the event "booking.rejected" should be published to RabbitMQ

  @conflict
  Scenario: Booking rejected when requested time overlaps the start of an existing booking
    Given classroom "CR-102" has an existing booking on "2026-06-05" from "13:00" to "14:00"
    When user "eve@example.com" tries to book classroom "CR-102" on "2026-06-05" from "12:30" to "13:30"
    Then the booking status should be "REJECTED"
    And the rejection reason should be "Time slot is already booked"

  @conflict
  Scenario: Booking rejected when requested time overlaps the end of an existing booking
    Given classroom "CR-103" has an existing booking on "2026-06-06" from "15:00" to "16:00"
    When user "frank@example.com" tries to book classroom "CR-103" on "2026-06-06" from "15:30" to "16:30"
    Then the booking status should be "REJECTED"
    And the rejection reason should be "Time slot is already booked"

  @conflict
  Scenario: Booking rejected when requested time is completely within an existing booking
    Given classroom "CR-101" has an existing booking on "2026-06-07" from "09:00" to "12:00"
    When user "grace@example.com" tries to book classroom "CR-101" on "2026-06-07" from "10:00" to "11:00"
    Then the booking status should be "REJECTED"
    And the rejection reason should be "Time slot is already booked"

  @conflict
  Scenario: Booking rejected when requested time completely encompasses an existing booking
    Given classroom "CR-102" has an existing booking on "2026-06-08" from "10:00" to "11:00"
    When user "henry@example.com" tries to book classroom "CR-102" on "2026-06-08" from "09:00" to "12:00"
    Then the booking status should be "REJECTED"
    And the rejection reason should be "Time slot is already booked"

  # ─────────────────────────────────────────────
  # Boundary / Edge Cases
  # ─────────────────────────────────────────────

  @edge-case
  Scenario: Booking allowed when new slot starts exactly when previous booking ends
    Given classroom "CR-101" has an existing booking on "2026-06-09" from "09:00" to "10:00"
    When user "iris@example.com" books classroom "CR-101" on "2026-06-09" from "10:00" to "11:00"
    Then the booking status should be "CONFIRMED"

  @edge-case
  Scenario: Booking allowed when new slot ends exactly when next booking starts
    Given classroom "CR-102" has an existing booking on "2026-06-10" from "11:00" to "12:00"
    When user "jack@example.com" books classroom "CR-102" on "2026-06-10" from "10:00" to "11:00"
    Then the booking status should be "CONFIRMED"

  # ─────────────────────────────────────────────
  # Multiple Bookings Scenario (Outline)
  # ─────────────────────────────────────────────

  @scenario-outline
  Scenario Outline: Booking outcome depends on classroom availability
    Given classroom "<classroomId>" availability status is "<availabilityStatus>" on "<date>" from "<startTime>" to "<endTime>"
    When user "<user>" requests to book classroom "<classroomId>" on "<date>" from "<startTime>" to "<endTime>"
    Then the booking status should be "<expectedStatus>"

    Examples:
      | classroomId | date       | startTime | endTime | user                  | availabilityStatus | expectedStatus |
      | CR-101      | 2026-07-01 | 08:00     | 09:00   | user1@example.com     | AVAILABLE          | CONFIRMED      |
      | CR-102      | 2026-07-01 | 08:00     | 09:00   | user2@example.com     | UNAVAILABLE        | REJECTED       |
      | CR-103      | 2026-07-02 | 13:00     | 14:00   | user3@example.com     | AVAILABLE          | CONFIRMED      |
      | CR-101      | 2026-07-03 | 16:00     | 17:00   | user4@example.com     | UNAVAILABLE        | REJECTED       |
      | CR-102      | 2026-07-04 | 09:00     | 10:30   | user5@example.com     | AVAILABLE          | CONFIRMED      |

