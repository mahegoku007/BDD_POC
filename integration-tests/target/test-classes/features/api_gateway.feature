@api @camel
Feature: API Gateway - Booking REST Endpoint
  As a client application
  I want to submit booking requests through the REST API
  So that bookings are processed by the system

  Background:
    Given the API gateway is running on port "8080"
    And the endpoint "POST /bookings" is available

  # ─────────────────────────────────────────────
  # Valid Requests
  # ─────────────────────────────────────────────

  @smoke @valid-request
  Scenario: Valid booking request returns 202 Accepted
    When I send a POST request to "/bookings" with body:
      """
      {
        "classroomId": "CR-101",
        "date": "2026-06-01",
        "timeSlot": {
          "startTime": "09:00",
          "endTime": "10:00"
        },
        "requestedBy": "alice@example.com"
      }
      """
    Then the HTTP response status should be "202"
    And the response body should contain field "bookingId"
    And the response body should contain field "status" with value "PENDING"

  @valid-request
  Scenario: Booking request is forwarded to booking.requested queue
    When I send a POST request to "/bookings" with body:
      """
      {
        "classroomId": "CR-102",
        "date": "2026-06-02",
        "timeSlot": {
          "startTime": "10:00",
          "endTime": "11:00"
        },
        "requestedBy": "bob@example.com"
      }
      """
    Then the HTTP response status should be "202"
    And an event should be published to the "booking.requested" queue

  # ─────────────────────────────────────────────
  # Input Validation
  # ─────────────────────────────────────────────

  @validation
  Scenario: Missing classroomId returns 400 Bad Request
    When I send a POST request to "/bookings" with body:
      """
      {
        "date": "2026-06-01",
        "timeSlot": {
          "startTime": "09:00",
          "endTime": "10:00"
        },
        "requestedBy": "alice@example.com"
      }
      """
    Then the HTTP response status should be "400"
    And the response body should contain field "error"

  @validation
  Scenario: Missing date returns 400 Bad Request
    When I send a POST request to "/bookings" with body:
      """
      {
        "classroomId": "CR-101",
        "timeSlot": {
          "startTime": "09:00",
          "endTime": "10:00"
        },
        "requestedBy": "alice@example.com"
      }
      """
    Then the HTTP response status should be "400"

  @validation
  Scenario: Missing requestedBy returns 400 Bad Request
    When I send a POST request to "/bookings" with body:
      """
      {
        "classroomId": "CR-101",
        "date": "2026-06-01",
        "timeSlot": {
          "startTime": "09:00",
          "endTime": "10:00"
        }
      }
      """
    Then the HTTP response status should be "400"

  @validation
  Scenario: Invalid date format returns 400 Bad Request
    When I send a POST request to "/bookings" with body:
      """
      {
        "classroomId": "CR-101",
        "date": "01-06-2026",
        "timeSlot": {
          "startTime": "09:00",
          "endTime": "10:00"
        },
        "requestedBy": "alice@example.com"
      }
      """
    Then the HTTP response status should be "400"

  @validation
  Scenario: End time before start time returns 400 Bad Request
    When I send a POST request to "/bookings" with body:
      """
      {
        "classroomId": "CR-101",
        "date": "2026-06-01",
        "timeSlot": {
          "startTime": "11:00",
          "endTime": "09:00"
        },
        "requestedBy": "alice@example.com"
      }
      """
    Then the HTTP response status should be "400"
    And the response body should contain "endTime must be after startTime"

  @validation
  Scenario: Empty request body returns 400 Bad Request
    When I send a POST request to "/bookings" with an empty body
    Then the HTTP response status should be "400"

  # ─────────────────────────────────────────────
  # Content-Type
  # ─────────────────────────────────────────────

  @content-type
  Scenario: Request with non-JSON content type returns 415 Unsupported Media Type
    When I send a POST request to "/bookings" with content type "text/plain" and body "classroomId=CR-101"
    Then the HTTP response status should be "415"

  # ─────────────────────────────────────────────
  # Camel Route Logging
  # ─────────────────────────────────────────────

  @logging
  Scenario: Camel route logs incoming booking request
    When I send a POST request to "/bookings" with body:
      """
      {
        "classroomId": "CR-103",
        "date": "2026-06-03",
        "timeSlot": {
          "startTime": "14:00",
          "endTime": "15:00"
        },
        "requestedBy": "carol@example.com"
      }
      """
    Then the Camel route log should contain "Received booking request for classroom CR-103"

  # ─────────────────────────────────────────────
  # Health Check
  # ─────────────────────────────────────────────

  @health
  Scenario: Health endpoint returns 200 OK
    When I send a GET request to "/actuator/health"
    Then the HTTP response status should be "200"
    And the response body should contain field "status" with value "UP"

