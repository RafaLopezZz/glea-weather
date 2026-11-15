CREATE TABLE public.stations (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    provider VARCHAR(30) NOT NULL,
    indicativo VARCHAR(20) NOT NULL,
    nombre TEXT,

    CONSTRAINT uq_stations_provider_indicativo
        UNIQUE (provider, indicativo),

    CONSTRAINT chk_stations_provider
        CHECK (
            provider <> ''
            AND provider = UPPER(BTRIM(provider))
        ),

    CONSTRAINT chk_stations_indicativo
        CHECK (
            indicativo <> ''
            AND indicativo = BTRIM(indicativo)
        )
);
