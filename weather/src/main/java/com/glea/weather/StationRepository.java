package com.glea.weather;

import java.sql.Types;
import java.util.Optional;

import org.springframework.jdbc.core.simple.JdbcClient;

public class StationRepository {

    private final JdbcClient jdbcClient;
    private final StationValidator validator = new StationValidator();

    public StationRepository(JdbcClient jdbcClient) {
        this.jdbcClient = jdbcClient;
    }

    public long save(String provider, String indicativo, String nombre) {
        validator.validate(provider, indicativo);

        return jdbcClient.sql("""
                INSERT INTO public.stations (provider, indicativo, nombre)
                VALUES (:provider, :indicativo, :nombre)
                RETURNING id
                """)
                .param("provider", provider)
                .param("indicativo", indicativo)
                .param("nombre", nombre, Types.VARCHAR)
                .query(Long.class)
                .single();
    }

    public Optional<Long> findId(String provider, String indicativo) {
        validator.validate(provider, indicativo);

        return jdbcClient.sql("""
                SELECT id
                FROM public.stations
                WHERE provider = :provider
                AND indicativo = :indicativo
                """)
                .param("provider", provider)
                .param("indicativo", indicativo)
                .query(Long.class)
                .optional();
    }
}
