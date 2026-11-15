-- 005: fixes a NULL gap in 004.
-- In SQL, "result = 'complete'" is NULL (not false) when result is NULL, and a
-- CHECK only rejects FALSE. So 004 accepted persistence_status = 'complete'
-- for a run with no result at all. IS NOT DISTINCT FROM treats NULL as a value.
BEGIN;

ALTER TABLE public.acquisition_runs
    DROP CONSTRAINT chk_acquisition_persist_only_complete,
    ADD CONSTRAINT chk_acquisition_persist_only_complete
        CHECK (persistence_status <> 'complete' OR result IS NOT DISTINCT FROM 'complete');

COMMIT;
