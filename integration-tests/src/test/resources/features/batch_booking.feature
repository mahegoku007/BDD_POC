@batch @camel @split-aggregate
Feature: Batch Booking - Split and Aggregate Pattern
  As a client application
  I want to submit multiple booking requests in a single API call
  So that I can efficiently process bulk classroom reservations

  Background:
    Given the API gateway is running on port "8080"
    And the endpoint "POST /bookings/batch" is available

  # ─────────────────────────────────────────────
  # Valid batch requests
  # ─────────────────────────────────────────────

  @smoke @batch-valid
  Scenario: Batch of 3 valid bookings returns array of 3 acceptances
    When I send a POST request to "/bookings/batch" with body:
      """
      [
        {
          "classroomId": "CR-BATCH-01",
          "date": "2026-08-01",
          "timeSlot": {"startTime": "09:00", "endTime": "10:00"},
          "requestedBy": "batch1@example.com"
        },
        {
          "classroomId": "CR-BATCH-02",
          "date": "2026-08-01",
          "timeSlot": {"startTime": "10:00", "endTime": "11:00"},
          "requestedBy": "batch2@example.com"
        },
        {
          "classroomId": "CR-BATCH-03",
          "date": "2026-08-01",
          "timeSlot": {"startTime": "11:00", "endTime": "12:00"},
          "requestedBy": "batch3@example.com"
        }
      ]
      """
    Then the HTTP response status should be "200"
    And the response body should be a JSON array of size 3
    And each item in the batch response should contain field "bookingId"

  @batch-mixed
  Scenario: Batch with one invalid booking returns mixed results
    When I send a POST request to "/bookings/batch" with body:
      """
      [
        {
          "classroomId": "CR-BATCH-OK",
          "date": "2026-08-02",
          "timeSlot": {"startTime": "09:00", "endTime": "10:00"},
          "requestedBy": "valid@example.com"
        },
        {
          "classroomId": "",
          "date": "2026-08-02",
          "timeSlot": {"startTime": "09:00", "endTime": "10:00"},
          "requestedBy": "invalid@example.com"
        }
      ]
      """
    Then the HTTP response status should be "200"
    And the response body should be a JSON array of size 2
    And the batch response should contain at least one error item

  @batch-single
  Scenario: Batch with single item works like single booking
    When I send a POST request to "/bookings/batch" with body:
      """
      [
        {
          "classroomId": "CR-BATCH-SINGLE",
          "date": "2026-08-03",
          "timeSlot": {"startTime": "13:00", "endTime": "14:00"},
          "requestedBy": "single@example.com"
        }
      ]
      """
    Then the HTTP response status should be "200"
    And the response body should be a JSON array of size 1
    And each item in the batch response should contain field "bookingId"

