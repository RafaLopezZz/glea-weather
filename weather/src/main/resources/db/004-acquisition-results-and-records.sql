-- 004: acquisition outcome, original files and technical weather records.
-- Scope: G2 acotado (decision 2026-11-15). Apply once to gleaweather_test
-- after 001-003. Rows are never deleted by cascade: evidence is kept.
BEGIN;

-- 1. Outcome of the acquisition and of the PostgreSQL persistence, kept apart.
ALTER TABLE public.acquisition_runs
    ADD COLUMN result VARCHAR(10),
    ADD COLUMN completed_at TIMESTAMPTZ,
    ADD COLUMN persistence_status VARCHAR(10) NOT NULL DEFAULT 'pending',
    ADD COLUMN persisted_at TIMESTAMPTZ,

    -- 'partial' is out of scope in the reduced G2.
    ADD CONSTRAINT chk_acquisition_result
        CHECK (result IN ('complete', 'failed')),
    ADD CONSTRAINT chk_acquisition_persistence_status
        CHECK (persistence_status IN ('pending', 'complete', 'failed')),

    -- A result exists if and only if its finishing time was observed.
    ADD CONSTRAINT chk_acquisition_result_completed_at
        CHECK ((result IS NULL) = (completed_at IS NULL)),
    -- persisted_at exists only for a confirmed persistence.
    ADD CONSTRAINT chk_acquisition_persisted_at
        CHECK ((persistence_status = 'complete') = (persisted_at IS NOT NULL)),
    -- Only a complete download can have its records persisted.
    ADD CONSTRAINT chk_acquisition_persist_only_complete
        CHECK (persistence_status <> 'complete' OR result = 'complete'),
    -- Times cannot precede the request.
    ADD CONSTRAINT chk_acquisition_completed_after_request
        CHECK (completed_at IS NULL OR completed_at >= requested_at),
    ADD CONSTRAINT chk_acquisition_persisted_after_completion
        CHECK (persisted_at IS NULL OR persisted_at >= completed_at);

-- 2. One row per original file saved on disk (one per requested chunk).
CREATE TABLE public.raw_files (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    run_id UUID NOT NULL REFERENCES public.acquisition_runs(run_id),
    relative_path TEXT NOT NULL,
    chunk_start DATE NOT NULL,
    chunk_end DATE NOT NULL,
    byte_size BIGINT NOT NULL,
    sha256 CHAR(64) NOT NULL,

    CONSTRAINT uq_raw_files_run_path UNIQUE (run_id, relative_path),
    CONSTRAINT uq_raw_files_run_chunk UNIQUE (run_id, chunk_start),
    -- Chunks use the same [start, end) convention as the request.
    CONSTRAINT chk_raw_files_chunk CHECK (chunk_start < chunk_end),
    CONSTRAINT chk_raw_files_size CHECK (byte_size > 0),
    -- Lowercase hexadecimal SHA-256.
    CONSTRAINT chk_raw_files_sha256 CHECK (sha256 ~ '^[0-9a-f]{64}$'),
    -- Relative to the RAW root: no absolute paths, drive letters or "..".
    CONSTRAINT chk_raw_files_relative_path CHECK (
        relative_path <> ''
        AND relative_path !~ '^[/\\]'
        AND relative_path !~ '^[A-Za-z]:'
        AND relative_path !~ '(^|[/\\])\.\.([/\\]|$)'
    )
);

-- 3. One row per element of the AEMET JSON array, in its original position.
-- payload is a queryable technical copy; the RAW file remains the original.
-- Repeated dates and special values (e.g. "Acum") are kept as received.
CREATE TABLE public.weather_records (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    raw_file_id BIGINT NOT NULL REFERENCES public.raw_files(id),
    position INTEGER NOT NULL,
    record_date DATE,
    payload JSONB NOT NULL,

    CONSTRAINT uq_weather_records_file_position UNIQUE (raw_file_id, position),
    CONSTRAINT chk_weather_records_position CHECK (position >= 0),
    CONSTRAINT chk_weather_records_payload CHECK (jsonb_typeof(payload) = 'object')
);

COMMIT;
