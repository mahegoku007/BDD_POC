package com.classroom.availability;

import com.classroom.availability.model.BookingRecord;
import com.classroom.availability.repository.BookingRecordRepository;
import com.classroom.availability.service.AvailabilityService;
import com.classroom.common.dto.AvailabilityCheckResponse;
import com.classroom.common.dto.BookingRequest;
import com.classroom.common.dto.TimeSlot;
import com.classroom.common.enums.AvailabilityStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration test for {@link AvailabilityService} using a real H2 database.
 *
 * <p>{@code @DataJpaTest} starts only the JPA slice, which is sufficient
 * because {@link AvailabilityService} only depends on the repository.
 */
@DataJpaTest
@Import(AvailabilityService.class)
@ActiveProfiles("test")
@DisplayName("AvailabilityService")
class AvailabilityServiceTest {

    @Autowired
    private AvailabilityService availabilityService;

    @Autowired
    private BookingRecordRepository repository;

    private static final String CLASSROOM = "CR-101";
    private static final LocalDate DATE = LocalDate.of(2026, 6, 1);

    @BeforeEach
    void clearDb() {
        repository.deleteAll();
    }

    // ── checkAvailability ─────────────────────────────────────────────────────

    @Nested
    @DisplayName("checkAvailability()")
    class CheckAvailability {

        @Test
        @DisplayName("returns AVAILABLE when no bookings exist")
        void availableWhenEmpty() {
            AvailabilityCheckResponse result =
                    availabilityService.checkAvailability(request("09:00", "10:00"));

            assertThat(result.getStatus()).isEqualTo(AvailabilityStatus.AVAILABLE);
            assertThat(result.getConflictReason()).isNull();
        }

        @Test
        @DisplayName("returns UNAVAILABLE for an exact duplicate slot")
        void unavailableExactDuplicate() {
            seedBooking("09:00", "10:00");

            AvailabilityCheckResponse result =
                    availabilityService.checkAvailability(request("09:00", "10:00"));

            assertThat(result.getStatus()).isEqualTo(AvailabilityStatus.UNAVAILABLE);
            assertThat(result.getConflictReason()).isEqualTo(AvailabilityService.CONFLICT_REASON);
        }

        @Test
        @DisplayName("returns UNAVAILABLE when new slot overlaps start of existing")
        void unavailableOverlapsStart() {
            seedBooking("10:00", "11:00");

            AvailabilityCheckResponse result =
                    availabilityService.checkAvailability(request("09:30", "10:30"));

            assertThat(result.getStatus()).isEqualTo(AvailabilityStatus.UNAVAILABLE);
        }

        @Test
        @DisplayName("returns UNAVAILABLE when new slot overlaps end of existing")
        void unavailableOverlapsEnd() {
            seedBooking("09:00", "10:00");

            AvailabilityCheckResponse result =
                    availabilityService.checkAvailability(request("09:30", "10:30"));

            assertThat(result.getStatus()).isEqualTo(AvailabilityStatus.UNAVAILABLE);
        }

        @Test
        @DisplayName("returns UNAVAILABLE when new slot is contained within existing")
        void unavailableContainedWithin() {
            seedBooking("08:00", "12:00");

            AvailabilityCheckResponse result =
                    availabilityService.checkAvailability(request("09:00", "10:00"));

            assertThat(result.getStatus()).isEqualTo(AvailabilityStatus.UNAVAILABLE);
        }

        @Test
        @DisplayName("returns UNAVAILABLE when new slot wraps existing")
        void unavailableWrapsExisting() {
            seedBooking("10:00", "11:00");

            AvailabilityCheckResponse result =
                    availabilityService.checkAvailability(request("09:00", "12:00"));

            assertThat(result.getStatus()).isEqualTo(AvailabilityStatus.UNAVAILABLE);
        }

        @Test
        @DisplayName("returns AVAILABLE when new slot starts exactly when existing ends (adjacent)")
        void availableAdjacentAfter() {
            seedBooking("09:00", "10:00");

            AvailabilityCheckResponse result =
                    availabilityService.checkAvailability(request("10:00", "11:00"));

            assertThat(result.getStatus()).isEqualTo(AvailabilityStatus.AVAILABLE);
        }

        @Test
        @DisplayName("returns AVAILABLE when new slot ends exactly when existing starts (adjacent)")
        void availableAdjacentBefore() {
            seedBooking("11:00", "12:00");

            AvailabilityCheckResponse result =
                    availabilityService.checkAvailability(request("10:00", "11:00"));

            assertThat(result.getStatus()).isEqualTo(AvailabilityStatus.AVAILABLE);
        }

        @Test
        @DisplayName("returns AVAILABLE for a different classroom at the same time")
        void availableDifferentClassroom() {
            seedBooking("CR-102", "09:00", "10:00");

            AvailabilityCheckResponse result =
                    availabilityService.checkAvailability(request("09:00", "10:00")); // CR-101

            assertThat(result.getStatus()).isEqualTo(AvailabilityStatus.AVAILABLE);
        }

        @Test
        @DisplayName("returns AVAILABLE for the same classroom on a different date")
        void availableDifferentDate() {
            // Seed on DATE
            seedBooking("09:00", "10:00");

            // Check on DATE + 1
            BookingRequest req = BookingRequest.builder()
                    .bookingId("BK-test")
                    .classroomId(CLASSROOM)
                    .date(DATE.plusDays(1))
                    .timeSlot(new TimeSlot(LocalTime.of(9, 0), LocalTime.of(10, 0)))
                    .requestedBy("alice@example.com")
                    .build();

            assertThat(availabilityService.checkAvailability(req).getStatus())
                    .isEqualTo(AvailabilityStatus.AVAILABLE);
        }
    }

    // ── confirmBooking ────────────────────────────────────────────────────────

    @Nested
    @DisplayName("confirmBooking()")
    class ConfirmBooking {

        @Test
        @DisplayName("persists the booking record to H2")
        void persistsRecord() {
            availabilityService.confirmBooking(request("09:00", "10:00"));

            assertThat(repository.count()).isEqualTo(1);
        }

        @Test
        @DisplayName("second request for same slot is rejected after first is confirmed")
        void secondRequestRejectedAfterConfirmation() {
            availabilityService.confirmBooking(request("09:00", "10:00"));

            AvailabilityCheckResponse result =
                    availabilityService.checkAvailability(request("09:00", "10:00"));

            assertThat(result.getStatus()).isEqualTo(AvailabilityStatus.UNAVAILABLE);
        }
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private BookingRequest request(String start, String end) {
        return BookingRequest.builder()
                .bookingId("BK-test01")
                .classroomId(CLASSROOM)
                .date(DATE)
                .timeSlot(new TimeSlot(LocalTime.parse(start), LocalTime.parse(end)))
                .requestedBy("tester@example.com")
                .build();
    }

    private void seedBooking(String start, String end) {
        seedBooking(CLASSROOM, start, end);
    }

    private void seedBooking(String classroomId, String start, String end) {
        repository.save(BookingRecord.builder()
                .bookingId("BK-seed-" + start.replace(":", ""))
                .classroomId(classroomId)
                .date(DATE)
                .startTime(LocalTime.parse(start))
                .endTime(LocalTime.parse(end))
                .requestedBy("seed@example.com")
                .confirmedAt(LocalDateTime.now())
                .build());
    }
}

