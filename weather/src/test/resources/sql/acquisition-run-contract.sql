\set ON_ERROR_STOP on

BEGIN;

DO $test$
DECLARE
    test_station_id BIGINT;
    missing_station_id BIGINT;
BEGIN
    IF current_database() <> 'gleaweather_test' THEN
        RAISE EXCEPTION 'Run this test only against gleaweather_test';
    END IF;

INSERT INTO public.stations (provider, indicativo, nombre)
VALUES ('AEMET', 'RUN-CONTRACT', NULL)
RETURNING id INTO test_station_id;

INSERT INTO public.acquisition_runs (
    run_id,
    station_id,
    requested_start,
    requested_end,
    requested_at
)
VALUES (
    '00000000-0000-0000-0000-000000000001',
    test_station_id,
    DATE '2025-01-01',
    DATE '2025-02-01',
    TIMESTAMPTZ '2025-02-01 10:00:00+00'
);

IF NOT EXISTS (
    SELECT 1
    FROM public.acquisition_runs
    WHERE run_id = UUID '00000000-0000-0000-0000-000000000001'
      AND station_id = test_station_id
      AND requested_start = DATE '2025-01-01'
      AND requested_end = DATE '2025-02-01'
      AND requested_at = TIMESTAMPTZ '2025-02-01 10:00:00+00'
) THEN
    RAISE EXCEPTION 'Expected the acquisition run to preserve all input values';
END IF;

RAISE NOTICE 'PASS: valid acquisition run preserves all input values';
BEGIN
    INSERT INTO public.acquisition_runs (
        run_id, station_id,
        requested_start, requested_end, requested_at
    )
    VALUES (
        '00000000-0000-0000-0000-000000000002',
        test_station_id,
        DATE '2025-01-01',
        DATE '2025-01-01',
        TIMESTAMPTZ '2025-02-01 10:00:00+00'
    );

    RAISE EXCEPTION 'Expected an empty requested period to be rejected';
EXCEPTION
    WHEN check_violation THEN
        RAISE NOTICE 'PASS: empty requested period is rejected';
END;

BEGIN
    INSERT INTO public.acquisition_runs (
        run_id, station_id,
        requested_start, requested_end, requested_at
    )
    VALUES (
        '00000000-0000-0000-0000-000000000003',
        test_station_id,
        DATE '2025-02-01',
        DATE '2025-01-01',
        TIMESTAMPTZ '2025-02-01 10:00:00+00'
    );

    RAISE EXCEPTION 'Expected an inverted requested period to be rejected';
EXCEPTION
    WHEN check_violation THEN
        RAISE NOTICE 'PASS: inverted requested period is rejected';
END;

BEGIN
    INSERT INTO public.acquisition_runs (
        run_id, station_id,
        requested_start, requested_end, requested_at
    )
    VALUES (
        '00000000-0000-0000-0000-000000000001',
        test_station_id,
        DATE '2025-01-01',
        DATE '2025-02-01',
        TIMESTAMPTZ '2025-02-01 10:00:00+00'
    );

    RAISE EXCEPTION 'Expected a duplicate run_id to be rejected';
EXCEPTION
    WHEN unique_violation THEN
        RAISE NOTICE 'PASS: duplicate run_id is rejected';
END;

BEGIN
    INSERT INTO public.acquisition_runs (run_id, station_id, requested_start, requested_end, requested_at) VALUES
        (NULL, test_station_id, DATE '2025-01-01', DATE '2025-01-02', TIMESTAMPTZ '2025-02-01 10:00:00+00');
    RAISE EXCEPTION 'Expected null run_id to be rejected';
EXCEPTION WHEN not_null_violation THEN
    RAISE NOTICE 'PASS: null run_id is rejected';
END;

BEGIN
    INSERT INTO public.acquisition_runs (run_id, station_id, requested_start, requested_end, requested_at) VALUES
        ('00000000-0000-0000-0000-000000000004', NULL, DATE '2025-01-01', DATE '2025-01-02', TIMESTAMPTZ '2025-02-01 10:00:00+00');
    RAISE EXCEPTION 'Expected null station_id to be rejected';
EXCEPTION WHEN not_null_violation THEN
    RAISE NOTICE 'PASS: null station_id is rejected';
END;

BEGIN
    INSERT INTO public.acquisition_runs (run_id, station_id, requested_start, requested_end, requested_at) VALUES
        ('00000000-0000-0000-0000-000000000004', test_station_id, NULL, DATE '2025-01-02', TIMESTAMPTZ '2025-02-01 10:00:00+00');
    RAISE EXCEPTION 'Expected null requested_start to be rejected';
EXCEPTION WHEN not_null_violation THEN
    RAISE NOTICE 'PASS: null requested_start is rejected';
END;

BEGIN
    INSERT INTO public.acquisition_runs (run_id, station_id, requested_start, requested_end, requested_at) VALUES
        ('00000000-0000-0000-0000-000000000004', test_station_id, DATE '2025-01-01', NULL, TIMESTAMPTZ '2025-02-01 10:00:00+00');
    RAISE EXCEPTION 'Expected null requested_end to be rejected';
EXCEPTION WHEN not_null_violation THEN
    RAISE NOTICE 'PASS: null requested_end is rejected';
END;

BEGIN
    INSERT INTO public.acquisition_runs (run_id, station_id, requested_start, requested_end, requested_at) VALUES
        ('00000000-0000-0000-0000-000000000004', test_station_id, DATE '2025-01-01', DATE '2025-01-02', NULL);
    RAISE EXCEPTION 'Expected null requested_at to be rejected';
EXCEPTION WHEN not_null_violation THEN
    RAISE NOTICE 'PASS: null requested_at is rejected';
END;

-- Create and remove only a fixture to obtain a definitely absent station id.
INSERT INTO public.stations (provider, indicativo, nombre)
VALUES ('AEMET', 'RUN-CONTRACT-MISSING', NULL)
RETURNING id INTO missing_station_id;
DELETE FROM public.stations WHERE id = missing_station_id;

BEGIN
    INSERT INTO public.acquisition_runs (run_id, station_id, requested_start, requested_end, requested_at) VALUES
        ('00000000-0000-0000-0000-000000000004', missing_station_id, DATE '2025-01-01', DATE '2025-01-02', TIMESTAMPTZ '2025-02-01 10:00:00+00');
    RAISE EXCEPTION 'Expected a nonexistent station to be rejected';
EXCEPTION WHEN foreign_key_violation THEN
    RAISE NOTICE 'PASS: nonexistent station is rejected';
END;

INSERT INTO public.acquisition_runs (run_id, station_id, requested_start, requested_end, requested_at)
VALUES
    ('00000000-0000-0000-0000-000000000004', test_station_id, DATE '2025-01-01', DATE '2025-01-02', TIMESTAMPTZ '2025-02-01 11:00:00.123456+01'),
    ('00000000-0000-0000-0000-000000000005', test_station_id, DATE '2025-01-01', DATE '2025-01-02', TIMESTAMPTZ '2025-02-01 11:00:00.123456+01');

IF (SELECT COUNT(*) FROM public.acquisition_runs
    WHERE run_id IN (UUID '00000000-0000-0000-0000-000000000004', UUID '00000000-0000-0000-0000-000000000005')
      AND station_id = test_station_id
      AND requested_start = DATE '2025-01-01'
      AND requested_end = DATE '2025-01-02'
      AND requested_at = TIMESTAMPTZ '2025-02-01 10:00:00.123456+00') <> 2 THEN
    RAISE EXCEPTION 'Expected two distinct one-day runs preserving all input values';
END IF;
RAISE NOTICE 'PASS: one-day period is valid and preserves all input values';
RAISE NOTICE 'PASS: distinct runs may share station and requested period';
END
$test$;

ROLLBACK;
