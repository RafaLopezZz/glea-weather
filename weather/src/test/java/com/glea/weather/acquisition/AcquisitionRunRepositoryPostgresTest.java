package com.glea.weather.acquisition;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.sql.Connection;
import java.sql.Savepoint;
import java.sql.SQLException;
import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.jdbc.datasource.SingleConnectionDataSource;

import com.glea.weather.station.StationRepository;

@EnabledIfSystemProperty(named = "acquisition.runs.postgres.test", matches = "true")
class AcquisitionRunRepositoryPostgresTest {

        @Test
        void preservesTwoDistinctOneDayRunsAndRejectsDuplicateAndMissingStation() throws Exception {
                String url = requiredEnvironment("GLEAWEATHER_TEST_JDBC_URL");
                assertTrue(url.startsWith("jdbc:postgresql://") && url.endsWith("/gleaweather_test"),
                                "Use an explicit PostgreSQL URL ending in /gleaweather_test without URL parameters");
                DriverManagerDataSource dataSource = new DriverManagerDataSource(url,
                                requiredEnvironment("GLEAWEATHER_TEST_DB_USER"),
                                requiredEnvironment("GLEAWEATHER_TEST_DB_PASSWORD"));

                try (Connection connection = dataSource.getConnection()) {
                        JdbcClient jdbc = JdbcClient.create(new SingleConnectionDataSource(connection, true));
                        assertEquals("gleaweather_test",
                                        jdbc.sql("SELECT current_database()").query(String.class).single());
                        connection.setAutoCommit(false);
                        UUID firstId = UUID.randomUUID();
                        UUID secondId = UUID.randomUUID();
                        String provider = "RUN_TEST_" + Long.toUnsignedString(System.nanoTime());
                        try {
                                StationRepository stations = new StationRepository(jdbc);
                                long stationId = stations.save(provider, "RUN-STATION", null);
                                long missingStationId = stations.save(provider, "MISSING-STATION", null);
                                assertEquals(1, jdbc.sql("DELETE FROM public.stations WHERE id = :id")
                                                .param("id", missingStationId).update());

                                AcquisitionRunRepository repository = new AcquisitionRunRepository(jdbc);
                                LocalDate start = LocalDate.of(2025, 1, 1);
                                LocalDate end = start.plusDays(1);
                                Instant requestedAt = Instant.parse("2025-02-01T10:00:00.123456Z");
                                AcquisitionRun first = new AcquisitionRun(firstId, stationId, start, end, requestedAt);
                                AcquisitionRun second = new AcquisitionRun(secondId, stationId, start, end,
                                                requestedAt);
                                for (AcquisitionRun run : new AcquisitionRun[] { first, second }) {
                                        assertEquals(1, repository.save(run));
                                        AcquisitionRun stored = jdbc
                                                        .sql("""
                                                                        SELECT run_id, station_id, requested_start, requested_end, requested_at
                                                                        FROM public.acquisition_runs WHERE run_id = :id
                                                                        """)
                                                        .param("id", run.runId())
                                                        .query((row, number) -> new AcquisitionRun(
                                                                        row.getObject("run_id", UUID.class),
                                                                        row.getLong("station_id"),
                                                                        row.getObject("requested_start",
                                                                                        LocalDate.class),
                                                                        row.getObject("requested_end", LocalDate.class),
                                                                        row.getObject("requested_at",
                                                                                        OffsetDateTime.class)
                                                                                        .toInstant()))
                                                        .single();
                                        assertEquals(run, stored);
                                }

                                Savepoint beforeDuplicate = connection.setSavepoint();
                                DuplicateKeyException duplicate = assertThrows(DuplicateKeyException.class,
                                                () -> repository.save(first));
                                assertEquals("23505", ((SQLException) duplicate.getMostSpecificCause()).getSQLState());
                                connection.rollback(beforeDuplicate);
                                connection.releaseSavepoint(beforeDuplicate);

                                Savepoint beforeMissingStation = connection.setSavepoint();
                                DataIntegrityViolationException missing = assertThrows(
                                                DataIntegrityViolationException.class,
                                                () -> repository.save(
                                                                new AcquisitionRun(UUID.randomUUID(), missingStationId,
                                                                                start, end, requestedAt)));
                                assertEquals("23503", ((SQLException) missing.getMostSpecificCause()).getSQLState());
                                connection.rollback(beforeMissingStation);
                                connection.releaseSavepoint(beforeMissingStation);

                                assertEquals(2L, jdbc.sql(
                                                "SELECT COUNT(*) FROM public.acquisition_runs WHERE station_id = :id")
                                                .param("id", stationId).query(Long.class).single());
                        } finally {
                                connection.rollback();
                        }
                        assertEquals(0L, jdbc.sql(
                                        "SELECT COUNT(*) FROM public.acquisition_runs WHERE run_id IN (:first, :second)")
                                        .param("first", firstId).param("second", secondId).query(Long.class).single());
                        assertEquals(0L, jdbc.sql("SELECT COUNT(*) FROM public.stations WHERE provider = :provider")
                                        .param("provider", provider).query(Long.class).single());
                }
        }

        private static String requiredEnvironment(String name) {
                String value = System.getenv(name);
                assertNotNull(value, "Set " + name + " explicitly before enabling the PostgreSQL test");
                return value;
        }
}
