@availability
Feature: Classroom Availability Check
  As the availability service
  I want to verify whether a classroom is free for the requested time slot
  So that double bookings are prevented

  Background:
    Given the availability service is running
    And the in-memory booking store is empty

  # ─────────────────────────────────────────────
  # No Prior Bookings
  # ─────────────────────────────────────────────

  @smoke
  Scenario: Classroom is available when no bookings exist
    When the availability service checks classroom "CR-101" on "2026-06-01" from "09:00" to "10:00"
    Then the availability status should be "AVAILABLE"

  # ─────────────────────────────────────────────
  # Overlap Detection
  # ─────────────────────────────────────────────

  @overlap
  Scenario: Classroom is unavailable for an exact duplicate slot
    Given a booking already exists for classroom "CR-101" on "2026-06-01" from "09:00" to "10:00"
    When the availability service checks classroom "CR-101" on "2026-06-01" from "09:00" to "10:00"
    Then the availability status should be "UNAVAILABLE"
    And the conflict details should include the existing booking time "09:00 - 10:00"

  @overlap
  Scenario: Classroom is unavailable when new slot overlaps start of existing booking
    Given a booking already exists for classroom "CR-102" on "2026-06-02" from "10:00" to "11:00"
    When the availability service checks classroom "CR-102" on "2026-06-02" from "09:30" to "10:30"
    Then the availability status should be "UNAVAILABLE"

  @overlap
  Scenario: Classroom is unavailable when new slot overlaps end of existing booking
    Given a booking already exists for classroom "CR-103" on "2026-06-03" from "14:00" to "15:00"
    When the availability service checks classroom "CR-103" on "2026-06-03" from "14:30" to "15:30"
    Then the availability status should be "UNAVAILABLE"

  @overlap
  Scenario: Classroom is unavailable when new slot is contained within existing booking
    Given a booking already exists for classroom "CR-101" on "2026-06-04" from "08:00" to "12:00"
    When the availability service checks classroom "CR-101" on "2026-06-04" from "09:00" to "10:00"
    Then the availability status should be "UNAVAILABLE"

  @overlap
  Scenario: Classroom is unavailable when new slot completely wraps an existing booking
    Given a booking already exists for classroom "CR-102" on "2026-06-05" from "10:00" to "11:00"
    When the availability service checks classroom "CR-102" on "2026-06-05" from "09:00" to "12:00"
    Then the availability status should be "UNAVAILABLE"

  # ─────────────────────────────────────────────
  # Adjacent Slots (Allowed)
  # ─────────────────────────────────────────────

  @boundary
  Scenario: Classroom is available when new slot starts exactly at end of existing booking
    Given a booking already exists for classroom "CR-101" on "2026-06-06" from "09:00" to "10:00"
    When the availability service checks classroom "CR-101" on "2026-06-06" from "10:00" to "11:00"
    Then the availability status should be "AVAILABLE"

  @boundary
  Scenario: Classroom is available when new slot ends exactly at start of existing booking
    Given a booking already exists for classroom "CR-101" on "2026-06-07" from "11:00" to "12:00"
    When the availability service checks classroom "CR-101" on "2026-06-07" from "10:00" to "11:00"
    Then the availability status should be "AVAILABLE"

  # ─────────────────────────────────────────────
  # Multiple Existing Bookings
  # ─────────────────────────────────────────────

  @multi-booking
  Scenario: Classroom is available in a free gap between two existing bookings
    Given the following bookings exist for classroom "CR-103":
      | date       | startTime | endTime |
      | 2026-06-08 | 08:00     | 09:00   |
      | 2026-06-08 | 11:00     | 12:00   |
    When the availability service checks classroom "CR-103" on "2026-06-08" from "09:00" to "11:00"
    Then the availability status should be "AVAILABLE"

  @multi-booking
  Scenario: Classroom is unavailable when slot conflicts with one of many existing bookings
    Given the following bookings exist for classroom "CR-103":
      | date       | startTime | endTime |
      | 2026-06-09 | 08:00     | 09:00   |
      | 2026-06-09 | 10:00     | 11:00   |
      | 2026-06-09 | 13:00     | 14:00   |
    When the availability service checks classroom "CR-103" on "2026-06-09" from "10:30" to "11:30"
    Then the availability status should be "UNAVAILABLE"

  # ─────────────────────────────────────────────
  # Cross-Day Isolation
  # ─────────────────────────────────────────────

  @isolation
  Scenario: Booking on a different date does not affect availability on another date
    Given a booking already exists for classroom "CR-101" on "2026-06-10" from "09:00" to "10:00"
    When the availability service checks classroom "CR-101" on "2026-06-11" from "09:00" to "10:00"
    Then the availability status should be "AVAILABLE"

