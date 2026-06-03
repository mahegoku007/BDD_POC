@audit @camel
Feature: Audit Logging - Booking Audit Trail via MongoDB
  As a system administrator
  I want all booking events to be recorded in an audit trail
  So that I can track the lifecycle of every booking for compliance

  Background:
    Given the API gateway is running on port "8080"
    And the audit service is running on port "8084"

  # ─────────────────────────────────────────────
  # Audit event recording
  # ─────────────────────────────────────────────

  @smoke @audit-created
  Scenario: Booking creation produces a CREATED audit event
    When I send a POST request to "/bookings" with body:
      """
      {
        "classroomId": "CR-AUDIT-101",
        "date": "2026-07-01",
        "timeSlot": {
          "startTime": "09:00",
          "endTime": "10:00"
        },
        "requestedBy": "auditor@example.com"
      }
      """
    Then the HTTP response status should be "202"
    And the response body should contain field "bookingId"
    When I query the audit trail for the last booking
    Then the audit trail should contain an event with action "CREATED"
    And the audit event should have source "api-gateway"
    And the audit event payload should contain "CR-AUDIT-101"

  @audit-trail
  Scenario: Audit trail returns events in chronological order
    When I send a POST request to "/bookings" with body:
      """
      {
        "classroomId": "CR-AUDIT-102",
        "date": "2026-07-02",
        "timeSlot": {
          "startTime": "14:00",
          "endTime": "15:00"
        },
        "requestedBy": "tracker@example.com"
      }
      """
    Then the HTTP response status should be "202"
    When I query the audit trail for the last booking
    Then the audit trail should have at least 1 event
    And the first audit event should have action "CREATED"

  @audit-not-found
  Scenario: Audit endpoint returns 404 for unknown booking ID
    When I query the audit trail for booking "BK-NONEXISTENT"
    Then the audit HTTP response status should be "404"

  @audit-classroom
  Scenario: Audit events can be queried by classroom ID
    When I send a POST request to "/bookings" with body:
      """
      {
        "classroomId": "CR-AUDIT-200",
        "date": "2026-07-03",
        "timeSlot": {
          "startTime": "10:00",
          "endTime": "11:00"
        },
        "requestedBy": "classroom-query@example.com"
      }
      """
    Then the HTTP response status should be "202"
    When I query audit events for classroom "CR-AUDIT-200"
    Then the classroom audit trail should not be empty

