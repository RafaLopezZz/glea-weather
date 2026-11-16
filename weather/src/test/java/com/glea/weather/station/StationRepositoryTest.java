package com.glea.weather.station;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.sql.Types;
import java.util.stream.Stream;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.simple.JdbcClient;

class StationRepositoryTest {

    private final JdbcClient jdbcClient = mock(JdbcClient.class);
    private final StationRepository repository = new StationRepository(jdbcClient);

    @ParameterizedTest
    @MethodSource("invalidIdentifiers")
    void rejectsInvalidIdentifiersBeforeUsingJdbc(String provider, String indicativo) {
        assertThrows(IllegalArgumentException.class, () -> repository.save(provider, indicativo, null));
        verifyNoInteractions(jdbcClient);
    }

    static Stream<Arguments> invalidIdentifiers() {
        return Stream.of(
                Arguments.of(null, "7211B"),
                Arguments.of("AEMET", null),
                Arguments.of("", "7211B"),
                Arguments.of("aemet", "7211B"),
                Arguments.of("AEMET", "\t"),
                Arguments.of("AEMET", "\u20030012b"),
                Arguments.of("A".repeat(31), "7211B"),
                Arguments.of("AEMET", "b".repeat(21)));
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(strings = { "Station O'Brien", "x'); DROP TABLE public.stations; --" })
    void bindsValuesWithoutChangingThemAndReturnsGeneratedId(String nombre) {
        JdbcClient.MappedQuerySpec<Long> query = prepareInsert(nombre);
        when(query.single()).thenReturn(42L);

        assertEquals(42L, repository.save("AEMET", "0012b", nombre));
        verify(query).single();
    }

    @Test
    void propagatesDatabaseDuplicateRejectionWithoutRetryOrUpdate() {
        JdbcClient.MappedQuerySpec<Long> query = prepareInsert(null);
        DuplicateKeyException duplicate = new DuplicateKeyException("Duplicate station");
        when(query.single()).thenThrow(duplicate);

        assertSame(duplicate, assertThrows(DuplicateKeyException.class,
                () -> repository.save("AEMET", "0012b", null)));
        verify(query).single();
    }

    @SuppressWarnings("unchecked")
    private JdbcClient.MappedQuerySpec<Long> prepareInsert(String nombre) {
        JdbcClient.StatementSpec statement = mock(JdbcClient.StatementSpec.class);
        JdbcClient.MappedQuerySpec<Long> query = mock(JdbcClient.MappedQuerySpec.class);
        when(jdbcClient.sql("""
                INSERT INTO public.stations (provider, indicativo, nombre)
                VALUES (:provider, :indicativo, :nombre)
                RETURNING id
                """)).thenReturn(statement);
        when(statement.param("provider", "AEMET")).thenReturn(statement);
        when(statement.param("indicativo", "0012b")).thenReturn(statement);
        when(statement.param("nombre", nombre, Types.VARCHAR)).thenReturn(statement);
        when(statement.query(Long.class)).thenReturn(query);
        return query;
    }

    @Test
    void rejectsNullProviderBeforeFindingStation() {
        assertThrows(IllegalArgumentException.class,
                () -> repository.findId(null, "7211B"));

        verifyNoInteractions(jdbcClient);
    }

    @Test
    @SuppressWarnings("unchecked")
    void returnsIdWhenStationExists() {
        // Arrange: preparar JDBC simulado
        JdbcClient.StatementSpec statement = mock(JdbcClient.StatementSpec.class);
        JdbcClient.MappedQuerySpec<Long> query = mock(JdbcClient.MappedQuerySpec.class);

        when(jdbcClient.sql("""
                SELECT id
                FROM public.stations
                WHERE provider = :provider
                AND indicativo = :indicativo
                """)).thenReturn(statement);

        when(statement.param("provider", "AEMET")).thenReturn(statement);
        when(statement.param("indicativo", "0012b")).thenReturn(statement);
        when(statement.query(Long.class)).thenReturn(query);
        when(query.optional()).thenReturn(Optional.of(42L));

        // Act: ejecutar nuestro método real
        Optional<Long> result = repository.findId("AEMET", "0012b");

        // Assert: comprobar el identificador recibido
        assertEquals(Optional.of(42L), result);
        verify(query).optional();
    }

    @Test
    @SuppressWarnings("unchecked")
    void returnsEmptyWhenStationDoesNotExist() {
        // Arrange: preparar JDBC simulado
        JdbcClient.StatementSpec statement = mock(JdbcClient.StatementSpec.class);
        JdbcClient.MappedQuerySpec<Long> query = mock(JdbcClient.MappedQuerySpec.class);

        when(jdbcClient.sql("""
                SELECT id
                FROM public.stations
                WHERE provider = :provider
                AND indicativo = :indicativo
                """)).thenReturn(statement);

        when(statement.param("provider", "AEMET")).thenReturn(statement);
        when(statement.param("indicativo", "0012b")).thenReturn(statement);
        when(statement.query(Long.class)).thenReturn(query);
        when(query.optional()).thenReturn(Optional.empty());

        // Act: ejecutar nuestro método real
        Optional<Long> result = repository.findId("AEMET", "0012b");

        // Assert: comprobar el identificador recibido
        assertEquals(Optional.empty(), result);
        verify(query).optional();
    }
}
