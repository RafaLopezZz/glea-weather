package com.glea.weather;

import java.time.ZoneOffset;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
public class AcquisitionRunRepository {
    private final JdbcClient jdbcClient;

    public AcquisitionRunRepository(JdbcClient jdbcClient) {
        this.jdbcClient = jdbcClient;
    }

    public int save(AcquisitionRun run) {
        if (run == null) {
            throw new IllegalArgumentException("Acquisition run must not be null.");
        }
        return jdbcClient.sql("""
                INSERT INTO public.acquisition_runs
                    (run_id, station_id, requested_start, requested_end, requested_at)
                VALUES (:runId, :stationId, :requestedStart, :requestedEnd, :requestedAt)
                """)
                .param("runId", run.runId())
                .param("stationId", run.stationId())
                .param("requestedStart", run.requestedStart())
                .param("requestedEnd", run.requestedEnd())
                // PostgreSQL JDBC supports OffsetDateTime for TIMESTAMPTZ, not Instant.
                .param("requestedAt", run.requestedAt().atOffset(ZoneOffset.UTC))
                .update();
    }
}
