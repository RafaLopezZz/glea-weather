package com.glea.weather;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.sql.Connection;
import java.sql.Savepoint;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.jdbc.datasource.SingleConnectionDataSource;

@EnabledIfSystemProperty(named = "stations.postgres.test", matches = "true")
class StationRepositoryPostgresTest {

    @Test
    void insertsReadsAndRejectsDuplicatesOnRealPostgres() throws Exception {
        String url = requiredEnvironment("GLEAWEATHER_TEST_JDBC_URL");
        assertTrue(url.startsWith("jdbc:postgresql://") && url.endsWith("/gleaweather_test"),
                "Use an explicit PostgreSQL URL ending in /gleaweather_test without URL parameters");
        DriverManagerDataSource dataSource = new DriverManagerDataSource(url,
                requiredEnvironment("GLEAWEATHER_TEST_DB_USER"),
                requiredEnvironment("GLEAWEATHER_TEST_DB_PASSWORD"));

        try (Connection connection = dataSource.getConnection()) {
            JdbcClient jdbc = JdbcClient.create(new SingleConnectionDataSource(connection, true));
            assertEquals("gleaweather_test", jdbc.sql("SELECT current_database()").query(String.class).single());
            connection.setAutoCommit(false);
            try {
                StationRepository repository = new StationRepository(jdbc);
                String provider = "JDBC_TEST_" + Long.toUnsignedString(System.nanoTime());
                String[] identifiers = { "g2-lowercase", "G2-MiXeD", "0012b" };
                for (int i = 0; i < identifiers.length; i++) {
                    String indicativo = identifiers[i];
                    String nombre = i == 0 ? null : "Station O'Brien '); --";
                    assertEquals(Optional.empty(), repository.findId(provider, indicativo));
                    long id = repository.save(provider, indicativo, nombre);
                    assertTrue(id > 0);
                    assertEquals(Optional.of(id), repository.findId(provider, indicativo));

                    jdbc.sql("SELECT provider, indicativo, nombre FROM public.stations WHERE id = :id")
                            .param("id", id)
                            .query((row, rowNumber) -> {
                                assertEquals(provider, row.getString("provider"));
                                assertEquals(indicativo, row.getString("indicativo"));
                                assertEquals(nombre, row.getString("nombre"));
                                return row.getString("indicativo");
                            }).single();

                    Savepoint beforeDuplicate = connection.setSavepoint();
                    assertThrows(DuplicateKeyException.class,
                            () -> repository.save(provider, indicativo, "Different name"));
                    connection.rollback(beforeDuplicate);
                    connection.releaseSavepoint(beforeDuplicate);

                    assertEquals(1L, jdbc.sql("""
                            SELECT COUNT(*) FROM public.stations
                            WHERE provider = :provider AND indicativo = :indicativo
                            """)
                            .param("provider", provider).param("indicativo", indicativo)
                            .query(Long.class).single());
                    assertEquals(nombre, jdbc.sql("SELECT nombre FROM public.stations WHERE id = :id")
                            .param("id", id).query(String.class).optional().orElse(null));
                }
            } finally {
                connection.rollback();
            }
        }
    }

    private static String requiredEnvironment(String name) {
        String value = System.getenv(name);
        assertNotNull(value, "Set " + name + " explicitly before enabling the PostgreSQL test");
        return value;
    }
}
