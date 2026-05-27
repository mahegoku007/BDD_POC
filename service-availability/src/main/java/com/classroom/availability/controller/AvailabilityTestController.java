package com.classroom.availability.controller;

import com.classroom.availability.model.BookingRecord;
import com.classroom.availability.repository.BookingRecordRepository;
import com.classroom.availability.service.AvailabilityService;
import com.classroom.common.dto.AvailabilityCheckResponse;
import com.classroom.common.dto.BookingRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Test-support REST endpoints for the Availability Service.
 *
 * <p>These endpoints are intentionally simple and unprotected – they exist
 * purely to allow Cucumber BDD tests to:
 * <ul>
 *   <li>Seed existing bookings into H2 without going through RabbitMQ</li>
 *   <li>Clear the booking store between scenarios ({@code @BeforeEach})</li>
 *   <li>Directly invoke an availability check and inspect the result</li>
 * </ul>
 *
 * <p>In a production environment these endpoints would be secured or removed.
 */
@Slf4j
@RestController
@RequestMapping("/test")
@RequiredArgsConstructor
public class AvailabilityTestController {

    private final BookingRecordRepository repository;
    private final AvailabilityService availabilityService;

    /**
     * Removes all booking records from the H2 store.
     * Called by Cucumber {@code Background} steps to start each scenario clean.
     */
    @DeleteMapping("/bookings")
    public ResponseEntity<Void> clearAllBookings() {
        long count = repository.count();
        repository.deleteAll();
        log.info("[TEST-CTRL] Cleared {} booking record(s) from availability store", count);
        return ResponseEntity.noContent().build();
    }

    /**
     * Seeds a single booking record directly into H2.
     * Used by "Given classroom X has an existing booking" steps.
     */
    @PostMapping("/bookings")
    public ResponseEntity<BookingRecord> seedBooking(@RequestBody BookingRecord record) {
        BookingRecord saved = repository.save(record);
        log.info("[TEST-CTRL] Seeded booking record: {}", saved.getBookingId());
        return ResponseEntity.status(HttpStatus.CREATED).body(saved);
    }

    /**
     * Returns all booking records currently in H2.
     * Useful for debugging and asserting state in step definitions.
     */
    @GetMapping("/bookings")
    public ResponseEntity<List<BookingRecord>> getAllBookings() {
        return ResponseEntity.ok(repository.findAll());
    }

    /**
     * Directly invokes the availability check logic without going through RabbitMQ.
     * Used by availability_check.feature "When the availability service checks …" steps.
     */
    @PostMapping("/availability/check")
    public ResponseEntity<AvailabilityCheckResponse> checkAvailability(
            @RequestBody BookingRequest request) {
        AvailabilityCheckResponse response = availabilityService.checkAvailability(request);
        return ResponseEntity.ok(response);
    }
}

