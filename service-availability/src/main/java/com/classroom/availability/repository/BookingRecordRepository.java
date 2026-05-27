package com.classroom.availability.repository;

import com.classroom.availability.model.BookingRecord;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

/**
 * Spring Data JPA repository for {@link BookingRecord}.
 *
 * <p>The central method is {@link #existsOverlappingBooking} which implements
 * the half-open interval overlap check used by the availability service:
 *
 * <pre>
 *   Overlap condition:   A.start &lt; B.end  AND  B.start &lt; A.end
 *   Adjacent slots:      A.end == B.start  →  NOT overlapping (allowed)
 * </pre>
 */
@Repository
public interface BookingRecordRepository extends JpaRepository<BookingRecord, String> {

    /**
     * Returns {@code true} if any confirmed booking for the given classroom
     * overlaps the requested time slot on the given date.
     *
     * <p>Half-open interval logic:
     * <ul>
     *   <li>{@code b.startTime < :endTime}   – existing booking starts before requested slot ends</li>
     *   <li>{@code b.endTime > :startTime}   – existing booking ends after requested slot starts</li>
     * </ul>
     *
     * @param classroomId identifier of the classroom to check
     * @param date        date of the requested booking
     * @param startTime   inclusive start of the requested time slot
     * @param endTime     exclusive end of the requested time slot
     * @return {@code true} if a conflict exists; {@code false} if the slot is free
     */
    @Query("""
            SELECT COUNT(b) > 0
            FROM BookingRecord b
            WHERE b.classroomId = :classroomId
              AND b.date        = :date
              AND b.startTime   < :endTime
              AND b.endTime     > :startTime
            """)
    boolean existsOverlappingBooking(@Param("classroomId") String classroomId,
                                     @Param("date")        LocalDate date,
                                     @Param("startTime")   LocalTime startTime,
                                     @Param("endTime")     LocalTime endTime);

    /**
     * Retrieves all confirmed bookings for a classroom on a specific date,
     * ordered by start time. Useful for diagnostics and integration tests.
     *
     * @param classroomId identifier of the classroom
     * @param date        date to query
     * @return list of booking records ordered by {@code startTime} ascending
     */
    @Query("""
            SELECT b
            FROM BookingRecord b
            WHERE b.classroomId = :classroomId
              AND b.date        = :date
            ORDER BY b.startTime ASC
            """)
    List<BookingRecord> findByClassroomIdAndDate(@Param("classroomId") String classroomId,
                                                 @Param("date")        LocalDate date);
}

