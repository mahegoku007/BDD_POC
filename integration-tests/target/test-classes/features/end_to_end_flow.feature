@end-to-end @integration
Feature: End-to-End Classroom Booking Integration
  As a user interacting with the system
  I want the entire booking flow to work seamlessly
  So that a booking request submitted via the API results in a notification

  Background:
    Given the complete system is running with all services:
      | service              | port |
      | api-gateway-camel    | 8080 |
      | service-booking      | 8081 |
      | service-availability | 8082 |
      | service-notification | 8083 |
    And RabbitMQ is running on "localhost" port "5672"
    And the booking store is empty

  # ─────────────────────────────────────────────
  # Full Successful Booking Flow
  # ─────────────────────────────────────────────

  @smoke @e2e-success
  Scenario: Complete flow - API to notification for a confirmed booking
    Given classroom "CR-101" is available on "2026-08-01" from "09:00" to "10:00"
    When I submit a booking request via the REST API:
      | classroomId | CR-101            |
      | date        | 2026-08-01        |
      | startTime   | 09:00             |
      | endTime     | 10:00             |
      | requestedBy | alice@example.com |
    Then the API should respond with status "202" and a bookingId
    And eventually the booking status should become "CONFIRMED"
    And a confirmation notification should be logged for "alice@example.com"
    And all RabbitMQ queues should be empty after processing

  # ─────────────────────────────────────────────
  # Full Rejected Booking Flow
  # ─────────────────────────────────────────────

  @smoke @e2e-rejection
  Scenario: Complete flow - API to notification for a rejected booking
    Given classroom "CR-102" has an existing booking on "2026-08-02" from "11:00" to "12:00"
    When I submit a booking request via the REST API:
      | classroomId | CR-102           |
      | date        | 2026-08-02       |
      | startTime   | 11:00            |
      | endTime     | 12:00            |
      | requestedBy | bob@example.com  |
    Then the API should respond with status "202" and a bookingId
    And eventually the booking status should become "REJECTED"
    And a rejection notification should be logged for "bob@example.com"
    And all RabbitMQ queues should be empty after processing

  # ─────────────────────────────────────────────
  # Concurrent Booking Requests
  # ─────────────────────────────────────────────

  @concurrency
  Scenario: Two simultaneous requests for the same slot - only one is confirmed
    Given classroom "CR-103" is available on "2026-08-03" from "14:00" to "15:00"
    When the following booking requests are submitted concurrently:
      | classroomId | date       | startTime | endTime | requestedBy           |
      | CR-103      | 2026-08-03 | 14:00     | 15:00   | user1@example.com     |
      | CR-103      | 2026-08-03 | 14:00     | 15:00   | user2@example.com     |
    Then exactly "1" booking should be confirmed
    And exactly "1" booking should be rejected
    And total notifications sent should be "2"

  # ─────────────────────────────────────────────
  # Multiple Sequential Bookings
  # ─────────────────────────────────────────────

  @sequential
  Scenario: Multiple bookings for different classrooms on the same day all succeed
    When the following booking requests are submitted in sequence:
      | classroomId | date       | startTime | endTime | requestedBy           |
      | CR-101      | 2026-08-04 | 09:00     | 10:00   | user1@example.com     |
      | CR-102      | 2026-08-04 | 09:00     | 10:00   | user2@example.com     |
      | CR-103      | 2026-08-04 | 09:00     | 10:00   | user3@example.com     |
    Then all "3" bookings should be confirmed
    And "3" confirmation notifications should have been sent

  @sequential
  Scenario: Booking the same classroom sequentially in non-overlapping slots all succeed
    When the following booking requests are submitted in sequence:
      | classroomId | date       | startTime | endTime | requestedBy           |
      | CR-101      | 2026-08-05 | 08:00     | 09:00   | user1@example.com     |
      | CR-101      | 2026-08-05 | 09:00     | 10:00   | user2@example.com     |
      | CR-101      | 2026-08-05 | 10:00     | 11:00   | user3@example.com     |
    Then all "3" bookings should be confirmed

  # ─────────────────────────────────────────────
  # System Resilience
  # ─────────────────────────────────────────────

  @resilience
  Scenario: Booking request is not lost when availability service is temporarily slow
    Given the availability service introduces a delay of "2" seconds
    When I submit a booking request via the REST API:
      | classroomId | CR-101             |
      | date        | 2026-08-06         |
      | startTime   | 10:00              |
      | endTime     | 11:00              |
      | requestedBy | carol@example.com  |
    Then the API should respond with status "202" and a bookingId
    And eventually within "10" seconds the booking status should become "CONFIRMED"

