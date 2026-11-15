\set ON_ERROR_STOP on

BEGIN;

DO $test$
DECLARE
    generated_id BIGINT;
    violated_constraint TEXT;
    violated_column TEXT;
    invalid_case RECORD;
    expected_indicativo TEXT;
    stored_indicativo TEXT;
BEGIN
    IF current_database() <> 'gleaweather_test' THEN
        RAISE EXCEPTION 'Run this test only against gleaweather_test';
    END IF;

    INSERT INTO public.stations (provider, indicativo, nombre)
    VALUES ('AEMET', 'G2-TEST-STATION', NULL)
    RETURNING id INTO generated_id;

    IF generated_id IS NULL THEN
        RAISE EXCEPTION 'Expected a generated station id';
    END IF;

    IF NOT EXISTS (
        SELECT 1 FROM public.stations
        WHERE id = generated_id
          AND provider = 'AEMET'
          AND indicativo = 'G2-TEST-STATION'
          AND nombre IS NULL
    ) THEN
        RAISE EXCEPTION 'Expected the stored station to match the input';
    END IF;

    RAISE NOTICE 'PASS: station without a name receives an id and is stored correctly';

    BEGIN
        INSERT INTO public.stations (provider, indicativo, nombre)
        VALUES ('AEMET', 'G2-TEST-STATION', 'Different name');

        RAISE EXCEPTION 'Expected duplicate provider and indicativo to be rejected';
    EXCEPTION
        WHEN unique_violation THEN
            GET STACKED DIAGNOSTICS violated_constraint = CONSTRAINT_NAME;
            IF violated_constraint IS DISTINCT FROM 'uq_stations_provider_indicativo' THEN
                RAISE;
            END IF;
            RAISE NOTICE 'PASS: duplicate provider and indicativo is rejected even with a different name';
    END;

    BEGIN
        INSERT INTO public.stations (provider, indicativo, nombre)
        VALUES (NULL, 'G2-NULL-PROVIDER', NULL);

        RAISE EXCEPTION 'Expected a null provider to be rejected';
    EXCEPTION
        WHEN not_null_violation THEN
            GET STACKED DIAGNOSTICS violated_column = COLUMN_NAME;
            IF violated_column IS DISTINCT FROM 'provider' THEN
                RAISE;
            END IF;
            RAISE NOTICE 'PASS: null provider is rejected';
    END;

    BEGIN
        INSERT INTO public.stations (provider, indicativo, nombre)
        VALUES ('AEMET', NULL, NULL);

        RAISE EXCEPTION 'Expected a null indicativo to be rejected';
    EXCEPTION
        WHEN not_null_violation THEN
            GET STACKED DIAGNOSTICS violated_column = COLUMN_NAME;
            IF violated_column IS DISTINCT FROM 'indicativo' THEN
                RAISE;
            END IF;
            RAISE NOTICE 'PASS: null indicativo is rejected';
    END;

    FOR invalid_case IN
        SELECT * FROM (VALUES
            ('empty provider', '', 'G2-CHECK-CASE', 'chk_stations_provider'),
            ('blank provider', ' ', 'G2-CHECK-CASE', 'chk_stations_provider'),
            ('provider with leading space', ' AEMET', 'G2-CHECK-CASE', 'chk_stations_provider'),
            ('provider with trailing space', 'AEMET ', 'G2-CHECK-CASE', 'chk_stations_provider'),
            ('lowercase provider', 'aemet', 'G2-CHECK-CASE', 'chk_stations_provider'),
            ('empty indicativo', 'AEMET', '', 'chk_stations_indicativo'),
            ('blank indicativo', 'AEMET', ' ', 'chk_stations_indicativo'),
            ('indicativo with leading space', 'AEMET', ' G2-CHECK-CASE', 'chk_stations_indicativo'),
            ('indicativo with trailing space', 'AEMET', 'G2-CHECK-CASE ', 'chk_stations_indicativo')
        ) AS cases (case_name, provider, indicativo, expected_constraint)
    LOOP
        BEGIN
            INSERT INTO public.stations (provider, indicativo, nombre)
            VALUES (invalid_case.provider, invalid_case.indicativo, NULL);

            RAISE EXCEPTION 'Expected rejection for %', invalid_case.case_name;
        EXCEPTION
            WHEN check_violation THEN
                GET STACKED DIAGNOSTICS violated_constraint = CONSTRAINT_NAME;
                IF violated_constraint IS DISTINCT FROM invalid_case.expected_constraint THEN
                    RAISE;
                END IF;
                RAISE NOTICE 'PASS: % is rejected', invalid_case.case_name;
        END;
    END LOOP;

    INSERT INTO public.stations (provider, indicativo, nombre)
    VALUES ('AEMET', 'G2-OTHER-STATION', NULL);

    IF NOT EXISTS (
        SELECT 1 FROM public.stations
        WHERE provider = 'AEMET' AND indicativo = 'G2-OTHER-STATION'
    ) THEN
        RAISE EXCEPTION 'Expected the same provider with a different indicativo to be stored';
    END IF;
    RAISE NOTICE 'PASS: same provider with different indicativo is accepted';

    INSERT INTO public.stations (provider, indicativo, nombre)
    VALUES ('TEST', 'G2-TEST-STATION', NULL);

    IF NOT EXISTS (
        SELECT 1 FROM public.stations
        WHERE provider = 'TEST' AND indicativo = 'G2-TEST-STATION'
    ) THEN
        RAISE EXCEPTION 'Expected a different provider with the same indicativo to be stored';
    END IF;
    RAISE NOTICE 'PASS: different provider with same indicativo is accepted';

    FOR expected_indicativo IN
        SELECT examples.indicativo FROM (VALUES
            ('g2-lowercase'),
            ('G2-MiXeD'),
            ('0012b')
        ) AS examples (indicativo)
    LOOP
        INSERT INTO public.stations (provider, indicativo, nombre)
        VALUES ('AEMET', expected_indicativo, NULL)
        RETURNING indicativo INTO stored_indicativo;

        IF stored_indicativo IS DISTINCT FROM expected_indicativo THEN
            RAISE EXCEPTION 'Expected indicativo % to remain unchanged, got %',
                expected_indicativo, stored_indicativo;
        END IF;
        RAISE NOTICE 'PASS: indicativo % is preserved exactly', expected_indicativo;
    END LOOP;
END;
$test$;

DO $whitespace_test$
DECLARE
    whitespace_code INTEGER;
    valid_case RECORD;
    invalid_case RECORD;
    violated_constraint TEXT;
    stored_provider TEXT;
    stored_indicativo TEXT;
BEGIN
    -- Enumerate independently from the migration's Unicode escape string.
    FOR whitespace_code IN
        SELECT code FROM UNNEST(ARRAY[
            9, 10, 11, 12, 13, 28, 29, 30, 31, 32, 5760,
            8192, 8193, 8194, 8195, 8196, 8197, 8198,
            8200, 8201, 8202, 8232, 8233, 8287, 12288
        ]) AS codes(code)
    LOOP
        FOR invalid_case IN
            SELECT * FROM (VALUES
                ('blank provider', CHR(whitespace_code), 'G2-WS', 'chk_stations_provider'),
                ('leading provider whitespace', CHR(whitespace_code) || 'AEMET', 'G2-WS', 'chk_stations_provider'),
                ('trailing provider whitespace', 'AEMET' || CHR(whitespace_code), 'G2-WS', 'chk_stations_provider'),
                ('blank indicativo', 'AEMET', CHR(whitespace_code), 'chk_stations_indicativo'),
                ('leading indicativo whitespace', 'AEMET', CHR(whitespace_code) || '0012b', 'chk_stations_indicativo'),
                ('trailing indicativo whitespace', 'AEMET', '0012b' || CHR(whitespace_code), 'chk_stations_indicativo')
            ) AS cases (case_name, provider, indicativo, expected_constraint)
        LOOP
            BEGIN
                INSERT INTO public.stations (provider, indicativo)
                VALUES (invalid_case.provider, invalid_case.indicativo);

                RAISE EXCEPTION 'Expected rejection for %, code point %',
                    invalid_case.case_name, whitespace_code;
            EXCEPTION
                WHEN check_violation THEN
                    GET STACKED DIAGNOSTICS violated_constraint = CONSTRAINT_NAME;
                    IF violated_constraint IS DISTINCT FROM invalid_case.expected_constraint THEN
                        RAISE;
                    END IF;
                    RAISE NOTICE 'PASS: % is rejected for code point %',
                        invalid_case.case_name, whitespace_code;
            END;
        END LOOP;

        INSERT INTO public.stations (provider, indicativo)
        VALUES ('G2' || CHR(whitespace_code) || 'SOURCE', '00' || CHR(whitespace_code) || 'aB')
        RETURNING provider, indicativo INTO stored_provider, stored_indicativo;

        IF stored_provider IS DISTINCT FROM 'G2' || CHR(whitespace_code) || 'SOURCE'
            OR stored_indicativo IS DISTINCT FROM '00' || CHR(whitespace_code) || 'aB' THEN
            RAISE EXCEPTION 'Internal whitespace must remain unchanged, code point %', whitespace_code;
        END IF;
        RAISE NOTICE 'PASS: internal whitespace is preserved in both fields for code point %', whitespace_code;
    END LOOP;

    -- These are NOT Character.isWhitespace: NEL, NBSP, Mongolian vowel
    -- separator, figure space, zero-width space, narrow NBSP and BOM.
    FOR whitespace_code IN
        SELECT code FROM UNNEST(ARRAY[133, 160, 6158, 8199, 8203, 8239, 65279]) AS codes(code)
    LOOP
        FOR valid_case IN
            SELECT * FROM (VALUES
                (CHR(whitespace_code), CHR(whitespace_code)),
                (CHR(whitespace_code) || 'G2' || CHR(whitespace_code),
                    CHR(whitespace_code) || '0012b' || CHR(whitespace_code))
            ) AS cases (provider, indicativo)
        LOOP
            INSERT INTO public.stations (provider, indicativo)
            VALUES (valid_case.provider, valid_case.indicativo)
            RETURNING provider, indicativo INTO stored_provider, stored_indicativo;

            IF stored_provider IS DISTINCT FROM valid_case.provider
                OR stored_indicativo IS DISTINCT FROM valid_case.indicativo THEN
                RAISE EXCEPTION 'Non-Java-whitespace must remain unchanged, code point %', whitespace_code;
            END IF;
            RAISE NOTICE 'PASS: non-Java-whitespace is accepted unchanged for code point %, length %',
                whitespace_code, CHAR_LENGTH(valid_case.provider);
        END LOOP;
    END LOOP;

    INSERT INTO public.stations (provider, indicativo)
    VALUES (REPEAT('A', 30), REPEAT('b', 20))
    RETURNING provider, indicativo INTO stored_provider, stored_indicativo;

    IF stored_provider IS DISTINCT FROM REPEAT('A', 30)
        OR stored_indicativo IS DISTINCT FROM REPEAT('b', 20) THEN
        RAISE EXCEPTION 'Identifiers at length limits must remain unchanged';
    END IF;
    RAISE NOTICE 'PASS: identifiers at length limits are accepted unchanged';

    FOR invalid_case IN
        SELECT * FROM (VALUES
            ('provider over 30 characters', REPEAT('A', 31), 'G2-LENGTH'),
            ('indicativo over 20 characters', 'AEMET', REPEAT('b', 21))
        ) AS cases (case_name, provider, indicativo)
    LOOP
        BEGIN
            INSERT INTO public.stations (provider, indicativo)
            VALUES (invalid_case.provider, invalid_case.indicativo);

            RAISE EXCEPTION 'Expected rejection for %', invalid_case.case_name;
        EXCEPTION
            WHEN string_data_right_truncation THEN
                RAISE NOTICE 'PASS: % is rejected without truncation', invalid_case.case_name;
        END;
    END LOOP;
END;
$whitespace_test$;

ROLLBACK;
