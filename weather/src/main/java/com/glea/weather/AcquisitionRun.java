package com.glea.weather;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/** Request metadata only; the end date is exclusive and does not imply coverage. */
public record AcquisitionRun(UUID runId, Long stationId, LocalDate requestedStart,
        LocalDate requestedEnd, Instant requestedAt) {

    public AcquisitionRun {
        if (runId == null || stationId == null || requestedStart == null
                || requestedEnd == null || requestedAt == null) {
            throw new IllegalArgumentException("All acquisition run fields are required.");
        }
        if (!requestedStart.isBefore(requestedEnd)) {
            throw new IllegalArgumentException("Requested start must be before the exclusive end.");
        }
        // Reject precision loss instead of silently rounding the caller's timestamp.
        if (requestedAt.getNano() % 1_000 != 0) {
            throw new IllegalArgumentException("Requested at must use PostgreSQL microsecond precision.");
        }
    }
}
