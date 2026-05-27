@messaging @rabbitmq
Feature: RabbitMQ Messaging Flow
  As the system
  I want booking events to flow correctly through RabbitMQ queues
  So that all services communicate reliably via event-driven messaging

  Background:
    Given RabbitMQ is running on "localhost" port "5672"
    And the following queues are declared:
      | queue              |
      | booking.requested  |
      | booking.confirmed  |
      | booking.rejected   |
    And all queues are empty

  # ─────────────────────────────────────────────
  # booking.requested queue
  # ─────────────────────────────────────────────

  @smoke
  Scenario: Booking service publishes event to booking.requested queue
    When a booking request is submitted:
      | classroomId | CR-101            |
      | date        | 2026-06-01        |
      | startTime   | 09:00             |
      | endTime     | 10:00             |
      | requestedBy | alice@example.com |
    Then an event should be present on the "booking.requested" queue
    And the event payload should contain classroomId "CR-101"
    And the event payload should contain requestedBy "alice@example.com"

  @smoke
  Scenario: Availability service consumes event from booking.requested queue
    Given an event is on the "booking.requested" queue with payload:
      | classroomId | CR-102            |
      | date        | 2026-06-02        |
      | startTime   | 10:00             |
      | endTime     | 11:00             |
      | requestedBy | bob@example.com   |
    And classroom "CR-102" is available at that time
    When the availability service consumes the event
    Then the "booking.requested" queue should be empty
    And an event should be present on the "booking.confirmed" queue

  # ─────────────────────────────────────────────
  # booking.confirmed queue
  # ─────────────────────────────────────────────

  @confirmed-flow
  Scenario: Confirmed event is published to booking.confirmed queue when slot is free
    Given an event is on the "booking.requested" queue with payload:
      | classroomId | CR-103             |
      | date        | 2026-06-03         |
      | startTime   | 13:00              |
      | endTime     | 14:00              |
      | requestedBy | carol@example.com  |
    And classroom "CR-103" is available at that time
    When the availability service processes the event
    Then an event should be present on the "booking.confirmed" queue
    And the "booking.rejected" queue should be empty

  @confirmed-flow
  Scenario: Notification service consumes event from booking.confirmed queue
    Given an event is on the "booking.confirmed" queue for "dave@example.com"
    When the notification service consumes the event
    Then the "booking.confirmed" queue should be empty
    And a confirmation notification should be logged for "dave@example.com"

  # ─────────────────────────────────────────────
  # booking.rejected queue
  # ─────────────────────────────────────────────

  @rejected-flow
  Scenario: Rejected event is published to booking.rejected queue when slot is taken
    Given an event is on the "booking.requested" queue with payload:
      | classroomId | CR-101            |
      | date        | 2026-06-04        |
      | startTime   | 11:00             |
      | endTime     | 12:00             |
      | requestedBy | eve@example.com   |
    And classroom "CR-101" already has a booking on "2026-06-04" from "11:00" to "12:00"
    When the availability service processes the event
    Then an event should be present on the "booking.rejected" queue
    And the "booking.confirmed" queue should be empty

  @rejected-flow
  Scenario: Notification service consumes event from booking.rejected queue
    Given an event is on the "booking.rejected" queue for "frank@example.com" with reason "Time slot is already booked"
    When the notification service consumes the event
    Then the "booking.rejected" queue should be empty
    And a rejection notification should be logged for "frank@example.com"

  # ─────────────────────────────────────────────
  # End-to-End Message Flow
  # ─────────────────────────────────────────────

  @e2e @smoke
  Scenario: Full end-to-end flow for a successful booking
    Given classroom "CR-102" is available on "2026-06-05" from "15:00" to "16:00"
    When a booking request is submitted:
      | classroomId | CR-102             |
      | date        | 2026-06-05         |
      | startTime   | 15:00              |
      | endTime     | 16:00              |
      | requestedBy | grace@example.com  |
    Then an event should be present on the "booking.requested" queue
    When the availability service processes the event from "booking.requested"
    Then an event should be present on the "booking.confirmed" queue
    When the notification service processes the event from "booking.confirmed"
    Then a confirmation notification should be logged for "grace@example.com"
    And all queues should be empty

  @e2e
  Scenario: Full end-to-end flow for a rejected booking
    Given classroom "CR-101" has an existing booking on "2026-06-06" from "09:00" to "10:00"
    When a booking request is submitted:
      | classroomId | CR-101            |
      | date        | 2026-06-06        |
      | startTime   | 09:00             |
      | endTime     | 10:00             |
      | requestedBy | henry@example.com |
    Then an event should be present on the "booking.requested" queue
    When the availability service processes the event from "booking.requested"
    Then an event should be present on the "booking.rejected" queue
    When the notification service processes the event from "booking.rejected"
    Then a rejection notification should be logged for "henry@example.com"
    And all queues should be empty

  # ─────────────────────────────────────────────
  # Message Payload Validation
  # ─────────────────────────────────────────────

  @payload-validation
  Scenario: Message payload uses JSON format
    When a booking request is submitted:
      | classroomId | CR-103            |
      | date        | 2026-06-07        |
      | startTime   | 08:00             |
      | endTime     | 09:00             |
      | requestedBy | iris@example.com  |
    Then the message on "booking.requested" queue should be valid JSON
    And the JSON payload should have field "classroomId" equal to "CR-103"
    And the JSON payload should have field "requestedBy" equal to "iris@example.com"
    And the JSON payload should have field "status" equal to "PENDING"

