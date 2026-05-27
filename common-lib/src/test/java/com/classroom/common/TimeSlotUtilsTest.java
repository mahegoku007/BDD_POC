package com.classroom.common;

import com.classroom.common.dto.TimeSlot;
import com.classroom.common.util.TimeSlotUtils;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.time.LocalTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Unit tests for {@link TimeSlotUtils}.
 *
 * <p>Covers:
 * <ul>
 *   <li>Validation (null, reversed, equal times)</li>
 *   <li>All overlap edge cases from the BDD scenarios</li>
 *   <li>Adjacent slots (boundary)</li>
 *   <li>Duration calculation</li>
 * </ul>
 */
@DisplayName("TimeSlotUtils")
class TimeSlotUtilsTest {

    // ── isValid ───────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("isValid()")
    class IsValid {

        @Test
        @DisplayName("returns true for a valid slot")
        void valid() {
            assertThat(TimeSlotUtils.isValid(slot("09:00", "10:00"))).isTrue();
        }

        @Test
        @DisplayName("returns false when slot is null")
        void nullSlot() {
            assertThat(TimeSlotUtils.isValid(null)).isFalse();
        }

        @Test
        @DisplayName("returns false when startTime is null")
        void nullStart() {
            assertThat(TimeSlotUtils.isValid(new TimeSlot(null, LocalTime.of(10, 0)))).isFalse();
        }

        @Test
        @DisplayName("returns false when endTime is null")
        void nullEnd() {
            assertThat(TimeSlotUtils.isValid(new TimeSlot(LocalTime.of(9, 0), null))).isFalse();
        }

        @Test
        @DisplayName("returns false when start equals end")
        void equalTimes() {
            assertThat(TimeSlotUtils.isValid(slot("10:00", "10:00"))).isFalse();
        }

        @Test
        @DisplayName("returns false when start is after end")
        void reversedTimes() {
            assertThat(TimeSlotUtils.isValid(slot("11:00", "09:00"))).isFalse();
        }
    }

    // ── overlaps ──────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("overlaps()")
    class Overlaps {

        @Test
        @DisplayName("exact same slot → overlaps")
        void exactSameSlot() {
            assertThat(TimeSlotUtils.overlaps(slot("09:00", "10:00"), slot("09:00", "10:00"))).isTrue();
        }

        @Test
        @DisplayName("new slot overlaps start of existing → overlaps")
        void newSlotOverlapsStart() {
            assertThat(TimeSlotUtils.overlaps(slot("08:30", "09:30"), slot("09:00", "10:00"))).isTrue();
        }

        @Test
        @DisplayName("new slot overlaps end of existing → overlaps")
        void newSlotOverlapsEnd() {
            assertThat(TimeSlotUtils.overlaps(slot("09:30", "10:30"), slot("09:00", "10:00"))).isTrue();
        }

        @Test
        @DisplayName("new slot contained within existing → overlaps")
        void newSlotContainedWithin() {
            assertThat(TimeSlotUtils.overlaps(slot("09:15", "09:45"), slot("09:00", "10:00"))).isTrue();
        }

        @Test
        @DisplayName("new slot wraps existing → overlaps")
        void newSlotWrapsExisting() {
            assertThat(TimeSlotUtils.overlaps(slot("08:00", "11:00"), slot("09:00", "10:00"))).isTrue();
        }

        @Test
        @DisplayName("new slot starts exactly when existing ends → NO overlap (boundary)")
        void adjacentStartEqualsEnd() {
            assertThat(TimeSlotUtils.overlaps(slot("10:00", "11:00"), slot("09:00", "10:00"))).isFalse();
        }

        @Test
        @DisplayName("new slot ends exactly when existing starts → NO overlap (boundary)")
        void adjacentEndEqualsStart() {
            assertThat(TimeSlotUtils.overlaps(slot("08:00", "09:00"), slot("09:00", "10:00"))).isFalse();
        }

        @Test
        @DisplayName("completely before existing → NO overlap")
        void completlyBefore() {
            assertThat(TimeSlotUtils.overlaps(slot("07:00", "08:00"), slot("09:00", "10:00"))).isFalse();
        }

        @Test
        @DisplayName("completely after existing → NO overlap")
        void completelyAfter() {
            assertThat(TimeSlotUtils.overlaps(slot("11:00", "12:00"), slot("09:00", "10:00"))).isFalse();
        }

        @Test
        @DisplayName("throws when first slot is invalid")
        void throwsOnInvalidA() {
            assertThatThrownBy(() -> TimeSlotUtils.overlaps(slot("10:00", "09:00"), slot("09:00", "10:00")))
                    .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        @DisplayName("throws when second slot is invalid")
        void throwsOnInvalidB() {
            assertThatThrownBy(() -> TimeSlotUtils.overlaps(slot("09:00", "10:00"), slot("11:00", "10:00")))
                    .isInstanceOf(IllegalArgumentException.class);
        }
    }

    // ── durationMinutes ───────────────────────────────────────────────────────

    @Nested
    @DisplayName("durationMinutes()")
    class DurationMinutes {

        @ParameterizedTest(name = "{0} – {1} = {2} min")
        @CsvSource({
                "09:00, 10:00, 60",
                "09:00, 09:30, 30",
                "08:00, 12:00, 240",
                "23:00, 23:01, 1"
        })
        @DisplayName("returns correct duration")
        void correctDuration(String start, String end, long expectedMinutes) {
            assertThat(TimeSlotUtils.durationMinutes(slot(start, end))).isEqualTo(expectedMinutes);
        }

        @Test
        @DisplayName("throws for an invalid slot")
        void throwsForInvalidSlot() {
            assertThatThrownBy(() -> TimeSlotUtils.durationMinutes(slot("10:00", "09:00")))
                    .isInstanceOf(IllegalArgumentException.class);
        }
    }

    // ── Helper ────────────────────────────────────────────────────────────────

    private static TimeSlot slot(String start, String end) {
        return new TimeSlot(LocalTime.parse(start), LocalTime.parse(end));
    }
}

