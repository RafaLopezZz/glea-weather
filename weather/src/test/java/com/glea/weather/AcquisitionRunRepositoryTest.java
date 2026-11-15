package com.glea.weather;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.UUID;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

class AcquisitionRunRepositoryTest {
    private static final UUID ID = UUID.fromString("13f2889a-168a-4b81-bfe6-81434222e682");
    private static final LocalDate START = LocalDate.of(2025, 1, 1);
    private static final LocalDate END = START.plusDays(1);
    private static final Instant REQUESTED_AT = Instant.parse("2025-02-01T10:00:00.123456Z");

    private final JdbcClient jdbc = mock(JdbcClient.class);
    private final AcquisitionRunRepository repository = new AcquisitionRunRepository(jdbc);

    @ParameterizedTest
    @MethodSource("invalidRuns")
    void rejectsInvalidDataBeforeJdbc(UUID id, Long stationId, LocalDate start, LocalDate end, Instant at) {
        assertThrows(IllegalArgumentException.class,
                () -> repository.save(new AcquisitionRun(id, stationId, start, end, at)));
        verifyNoInteractions(jdbc);
    }

    static Stream<Arguments> invalidRuns() {
        return Stream.of(
                Arguments.of(null, 42L, START, END, REQUESTED_AT),
                Arguments.of(ID, null, START, END, REQUESTED_AT),
                Arguments.of(ID, 42L, null, END, REQUESTED_AT),
                Arguments.of(ID, 42L, START, null, REQUESTED_AT),
                Arguments.of(ID, 42L, START, END, null),
                Arguments.of(ID, 42L, START, START, REQUESTED_AT),
                Arguments.of(ID, 42L, END, START, REQUESTED_AT));
    }

    @Test
    void rejectsNullRunBeforeJdbc() {
        assertThrows(IllegalArgumentException.class, () -> repository.save(null));
        verifyNoInteractions(jdbc);
    }

    @Test
    void rejectsTimestampThatPostgresWouldRoundBeforeJdbc() {
        IllegalArgumentException failure = assertThrows(IllegalArgumentException.class,
                () -> repository.save(new AcquisitionRun(ID, 42L, START, END,
                        Instant.parse("2025-02-01T10:00:00.123456789Z"))));
        assertEquals("Requested at must use PostgreSQL microsecond precision.", failure.getMessage());
        verifyNoInteractions(jdbc);
    }

    @Test
    void bindsExactCallerValuesForOneDayWithoutGeneratingIdentityOrTime() {
        AcquisitionRun run = new AcquisitionRun(ID, 42L, START, END, REQUESTED_AT);
        JdbcClient.StatementSpec statement = prepareInsert(run);
        when(statement.update()).thenReturn(1);

        assertEquals(1, repository.save(run));
        assertEquals(ID, run.runId());
        assertEquals(42L, run.stationId());
        assertEquals(START, run.requestedStart());
        assertEquals(END, run.requestedEnd());
        assertEquals(REQUESTED_AT, run.requestedAt());
        verify(statement).update();
        verify(jdbc).sql(anyString());
        verifyNoMoreInteractions(jdbc);
    }

    @ParameterizedTest
    @MethodSource("databaseRejections")
    void propagatesDatabaseRejectionsWithoutPrequeryRetryOrUpdate(DataIntegrityViolationException failure) {
        AcquisitionRun run = new AcquisitionRun(ID, 42L, START, END, REQUESTED_AT);
        JdbcClient.StatementSpec statement = prepareInsert(run);
        when(statement.update()).thenThrow(failure);

        assertSame(failure, assertThrows(DataIntegrityViolationException.class, () -> repository.save(run)));
        verify(statement).update();
        verify(jdbc).sql(anyString());
        verifyNoMoreInteractions(jdbc);
    }

    static Stream<Arguments> databaseRejections() {
        return Stream.of(Arguments.of(new DuplicateKeyException("Duplicate run")),
                Arguments.of(new DataIntegrityViolationException("Missing station")));
    }

    @Test
    void constructsRepositoryBeanWithoutAccessingJdbc() {
        new ApplicationContextRunner()
                .withUserConfiguration(AcquisitionRunRepository.class)
                .withBean(JdbcClient.class, () -> jdbc)
                .run(context -> {
                    assertEquals(1, context.getBeansOfType(AcquisitionRunRepository.class).size());
                    verifyNoInteractions(jdbc);
                });
    }

    private JdbcClient.StatementSpec prepareInsert(AcquisitionRun run) {
        JdbcClient.StatementSpec statement = mock(JdbcClient.StatementSpec.class);
        when(jdbc.sql(anyString())).thenAnswer(invocation -> {
            assertEquals("""
                    INSERT INTO public.acquisition_runs
                        (run_id, station_id, requested_start, requested_end, requested_at)
                    VALUES (:runId, :stationId, :requestedStart, :requestedEnd, :requestedAt)
                    """, invocation.getArgument(0));
            return statement;
        });
        when(statement.param("runId", run.runId())).thenReturn(statement);
        when(statement.param("stationId", run.stationId())).thenReturn(statement);
        when(statement.param("requestedStart", run.requestedStart())).thenReturn(statement);
        when(statement.param("requestedEnd", run.requestedEnd())).thenReturn(statement);
        when(statement.param("requestedAt", run.requestedAt().atOffset(ZoneOffset.UTC))).thenReturn(statement);
        return statement;
    }
}
