\set ON_ERROR_STOP on

-- Contract test for migrations 004 and 005. Everything is rolled back at the end.
BEGIN;

DO $test$
DECLARE
    st BIGINT;
    run_ok CONSTANT UUID := '00000000-0000-0000-0000-000000000401';
    run_tmp CONSTANT UUID := '00000000-0000-0000-0000-000000000402';
    req CONSTANT TIMESTAMPTZ := TIMESTAMPTZ '2025-02-01 10:00:00+00';
    hash_ok CONSTANT TEXT := repeat('a', 64);
    file_id BIGINT;
    r RECORD;
BEGIN
    IF current_database() <> 'gleaweather_test' THEN
        RAISE EXCEPTION 'Run this test only against gleaweather_test';
    END IF;

    INSERT INTO public.stations (provider, indicativo, nombre)
    VALUES ('AEMET', 'CONTRACT-004', NULL)
    RETURNING id INTO st;

    -- ---------- acquisition_runs ----------
    INSERT INTO public.acquisition_runs (run_id, station_id, requested_start, requested_end, requested_at)
    VALUES (run_ok, st, DATE '2025-01-01', DATE '2025-02-01', req);

    SELECT result, completed_at, persistence_status, persisted_at INTO r
    FROM public.acquisition_runs WHERE run_id = run_ok;
    IF r.result IS NOT NULL OR r.completed_at IS NOT NULL
       OR r.persistence_status <> 'pending' OR r.persisted_at IS NOT NULL THEN
        RAISE EXCEPTION 'New run must start with no result and pending persistence';
    END IF;
    RAISE NOTICE 'PASS: new run starts unfinished and pending';

    BEGIN
        UPDATE public.acquisition_runs SET result = 'complete' WHERE run_id = run_ok;
        RAISE EXCEPTION 'Expected result without completed_at to be rejected';
    EXCEPTION WHEN check_violation THEN
        RAISE NOTICE 'PASS: result without completed_at is rejected';
    END;

    BEGIN
        UPDATE public.acquisition_runs SET completed_at = req + INTERVAL '1 minute' WHERE run_id = run_ok;
        RAISE EXCEPTION 'Expected completed_at without result to be rejected';
    EXCEPTION WHEN check_violation THEN
        RAISE NOTICE 'PASS: completed_at without result is rejected';
    END;

    BEGIN
        UPDATE public.acquisition_runs SET result = 'partial', completed_at = req + INTERVAL '1 minute'
        WHERE run_id = run_ok;
        RAISE EXCEPTION 'Expected partial to be rejected';
    EXCEPTION WHEN check_violation THEN
        RAISE NOTICE 'PASS: result partial is out of scope and rejected';
    END;

    BEGIN
        UPDATE public.acquisition_runs SET result = 'complete', completed_at = req - INTERVAL '1 minute'
        WHERE run_id = run_ok;
        RAISE EXCEPTION 'Expected completed_at before requested_at to be rejected';
    EXCEPTION WHEN check_violation THEN
        RAISE NOTICE 'PASS: completed_at before requested_at is rejected';
    END;

    BEGIN
        UPDATE public.acquisition_runs SET persisted_at = req + INTERVAL '2 minutes' WHERE run_id = run_ok;
        RAISE EXCEPTION 'Expected persisted_at while pending to be rejected';
    EXCEPTION WHEN check_violation THEN
        RAISE NOTICE 'PASS: persisted_at while pending is rejected';
    END;

    BEGIN
        UPDATE public.acquisition_runs SET persistence_status = 'complete' WHERE run_id = run_ok;
        RAISE EXCEPTION 'Expected complete persistence without persisted_at to be rejected';
    EXCEPTION WHEN check_violation THEN
        RAISE NOTICE 'PASS: complete persistence without persisted_at is rejected';
    END;

    INSERT INTO public.acquisition_runs (run_id, station_id, requested_start, requested_end, requested_at,
                                         result, completed_at)
    VALUES (run_tmp, st, DATE '2025-01-01', DATE '2025-02-01', req, 'failed', req + INTERVAL '1 minute');
    BEGIN
        UPDATE public.acquisition_runs
        SET persistence_status = 'complete', persisted_at = req + INTERVAL '2 minutes'
        WHERE run_id = run_tmp;
        RAISE EXCEPTION 'Expected persisting a failed download to be rejected';
    EXCEPTION WHEN check_violation THEN
        RAISE NOTICE 'PASS: a failed download cannot be marked as persisted';
    END;

    BEGIN
        UPDATE public.acquisition_runs
        SET persistence_status = 'complete', persisted_at = req + INTERVAL '2 minutes'
        WHERE run_id = run_ok;
        RAISE EXCEPTION 'Expected persisting a run without result to be rejected (005)';
    EXCEPTION WHEN check_violation THEN
        RAISE NOTICE 'PASS: a run without result cannot be marked as persisted';
    END;

    UPDATE public.acquisition_runs SET result = 'complete', completed_at = req + INTERVAL '1 minute'
    WHERE run_id = run_ok;
    BEGIN
        UPDATE public.acquisition_runs
        SET persistence_status = 'complete', persisted_at = req + INTERVAL '30 seconds'
        WHERE run_id = run_ok;
        RAISE EXCEPTION 'Expected persisted_at before completed_at to be rejected';
    EXCEPTION WHEN check_violation THEN
        RAISE NOTICE 'PASS: persisted_at before completed_at is rejected';
    END;
    UPDATE public.acquisition_runs
    SET persistence_status = 'complete', persisted_at = req + INTERVAL '2 minutes'
    WHERE run_id = run_ok;
    RAISE NOTICE 'PASS: complete download followed by confirmed persistence is accepted';

    -- ---------- raw_files ----------
    INSERT INTO public.raw_files (run_id, relative_path, chunk_start, chunk_end, byte_size, sha256)
    VALUES (run_ok, '7211B/' || run_ok || '/2025-01-01_2025-02-01.json',
            DATE '2025-01-01', DATE '2025-02-01', 1234, hash_ok)
    RETURNING id INTO file_id;
    RAISE NOTICE 'PASS: valid raw file is accepted';

    BEGIN
        INSERT INTO public.raw_files (run_id, relative_path, chunk_start, chunk_end, byte_size, sha256)
        VALUES (run_ok, '7211B/' || run_ok || '/2025-01-01_2025-02-01.json',
                DATE '2025-03-01', DATE '2025-04-01', 10, hash_ok);
        RAISE EXCEPTION 'Expected duplicate path in the same run to be rejected';
    EXCEPTION WHEN unique_violation THEN
        RAISE NOTICE 'PASS: duplicate path in the same run is rejected';
    END;

    BEGIN
        INSERT INTO public.raw_files (run_id, relative_path, chunk_start, chunk_end, byte_size, sha256)
        VALUES (run_ok, 'other.json', DATE '2025-01-01', DATE '2025-03-01', 10, hash_ok);
        RAISE EXCEPTION 'Expected duplicate chunk start in the same run to be rejected';
    EXCEPTION WHEN unique_violation THEN
        RAISE NOTICE 'PASS: duplicate chunk in the same run is rejected';
    END;

    BEGIN
        INSERT INTO public.raw_files (run_id, relative_path, chunk_start, chunk_end, byte_size, sha256)
        VALUES (run_ok, 'x.json', DATE '2025-05-01', DATE '2025-05-01', 10, hash_ok);
        RAISE EXCEPTION 'Expected empty chunk to be rejected';
    EXCEPTION WHEN check_violation THEN
        RAISE NOTICE 'PASS: empty or inverted chunk is rejected';
    END;

    BEGIN
        INSERT INTO public.raw_files (run_id, relative_path, chunk_start, chunk_end, byte_size, sha256)
        VALUES (run_ok, 'x.json', DATE '2025-05-01', DATE '2025-06-01', 0, hash_ok);
        RAISE EXCEPTION 'Expected zero-byte file to be rejected';
    EXCEPTION WHEN check_violation THEN
        RAISE NOTICE 'PASS: zero-byte file is rejected';
    END;

    BEGIN
        INSERT INTO public.raw_files (run_id, relative_path, chunk_start, chunk_end, byte_size, sha256)
        VALUES (run_ok, 'x.json', DATE '2025-05-01', DATE '2025-06-01', 10, upper(hash_ok));
        RAISE EXCEPTION 'Expected uppercase SHA-256 to be rejected';
    EXCEPTION WHEN check_violation THEN
        RAISE NOTICE 'PASS: non-canonical SHA-256 is rejected';
    END;

    BEGIN
        INSERT INTO public.raw_files (run_id, relative_path, chunk_start, chunk_end, byte_size, sha256)
        VALUES (run_ok, '../outside.json', DATE '2025-05-01', DATE '2025-06-01', 10, hash_ok);
        RAISE EXCEPTION 'Expected path escaping the RAW root to be rejected';
    EXCEPTION WHEN check_violation THEN
        RAISE NOTICE 'PASS: path with .. is rejected';
    END;

    BEGIN
        INSERT INTO public.raw_files (run_id, relative_path, chunk_start, chunk_end, byte_size, sha256)
        VALUES (run_ok, 'C:\raw\x.json', DATE '2025-05-01', DATE '2025-06-01', 10, hash_ok);
        RAISE EXCEPTION 'Expected absolute Windows path to be rejected';
    EXCEPTION WHEN check_violation THEN
        RAISE NOTICE 'PASS: absolute path is rejected';
    END;

    BEGIN
        INSERT INTO public.raw_files (run_id, relative_path, chunk_start, chunk_end, byte_size, sha256)
        VALUES ('00000000-0000-0000-0000-0000000004ff', 'x.json', DATE '2025-05-01', DATE '2025-06-01', 10, hash_ok);
        RAISE EXCEPTION 'Expected unknown run to be rejected';
    EXCEPTION WHEN foreign_key_violation THEN
        RAISE NOTICE 'PASS: raw file of an unknown run is rejected';
    END;

    -- ---------- weather_records ----------
    -- Same date at two positions: both are kept (duplicates are audited in Python).
    INSERT INTO public.weather_records (raw_file_id, position, record_date, payload) VALUES
        (file_id, 0, DATE '2025-01-01', '{"fecha":"2025-01-01","indicativo":"7211B","prec":"Acum"}'),
        (file_id, 1, DATE '2025-01-01', '{"fecha":"2025-01-01","indicativo":"7211B","prec":"0,0"}'),
        (file_id, 2, NULL,              '{"fecha":"no-es-fecha","indicativo":"7211B"}');
    IF (SELECT count(*) FROM public.weather_records WHERE raw_file_id = file_id) <> 3
       OR (SELECT payload->>'prec' FROM public.weather_records WHERE raw_file_id = file_id AND position = 0) <> 'Acum' THEN
        RAISE EXCEPTION 'Expected repeated dates, special values and unknown dates to be kept as received';
    END IF;
    RAISE NOTICE 'PASS: repeated dates, special values and unknown dates are kept';

    BEGIN
        INSERT INTO public.weather_records (raw_file_id, position, record_date, payload)
        VALUES (file_id, 0, DATE '2025-01-02', '{"fecha":"2025-01-02"}');
        RAISE EXCEPTION 'Expected duplicate position to be rejected';
    EXCEPTION WHEN unique_violation THEN
        RAISE NOTICE 'PASS: loading the same position twice is rejected (no silent duplicates)';
    END;

    BEGIN
        INSERT INTO public.weather_records (raw_file_id, position, record_date, payload)
        VALUES (file_id, -1, NULL, '{}');
        RAISE EXCEPTION 'Expected negative position to be rejected';
    EXCEPTION WHEN check_violation THEN
        RAISE NOTICE 'PASS: negative position is rejected';
    END;

    BEGIN
        INSERT INTO public.weather_records (raw_file_id, position, record_date, payload)
        VALUES (file_id, 9, NULL, '[1,2]');
        RAISE EXCEPTION 'Expected non-object payload to be rejected';
    EXCEPTION WHEN check_violation THEN
        RAISE NOTICE 'PASS: non-object payload is rejected';
    END;

    BEGIN
        DELETE FROM public.raw_files WHERE id = file_id;
        RAISE EXCEPTION 'Expected deleting a referenced raw file to be rejected';
    EXCEPTION WHEN foreign_key_violation THEN
        RAISE NOTICE 'PASS: evidence is protected (raw file with records cannot be deleted)';
    END;

    BEGIN
        DELETE FROM public.acquisition_runs WHERE run_id = run_ok;
        RAISE EXCEPTION 'Expected deleting a run with files to be rejected';
    EXCEPTION WHEN foreign_key_violation THEN
        RAISE NOTICE 'PASS: evidence is protected (run with files cannot be deleted)';
    END;
END;
$test$;

ROLLBACK;
