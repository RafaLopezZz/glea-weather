CREATE TABLE public.acquisition_runs (
    run_id UUID PRIMARY KEY,
    station_id BIGINT NOT NULL REFERENCES public.stations(id),
    requested_start DATE NOT NULL,
    requested_end DATE NOT NULL,
    requested_at TIMESTAMPTZ NOT NULL,

    CONSTRAINT chk_acquisition_requested_period
        CHECK (requested_start < requested_end)
);