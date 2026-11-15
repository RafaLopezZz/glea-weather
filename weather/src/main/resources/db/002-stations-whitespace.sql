BEGIN;

-- Java 25 Character.isWhitespace(int), used by String.isBlank() and strip():
-- U+0009-000D, U+001C-0020, U+1680, U+2000-2006, U+2008-200A,
-- U+2028-2029, U+205F, U+3000. NBSP (00A0, 2007, 202F) is excluded.
-- BTRIM's explicit character set removes only these characters at the edges.
-- Equality rejects them without changing input; empty/all-whitespace fails too.
-- Recheck this set when upgrading Java's Unicode definitions.
-- Existing VARCHAR(n) can discard excess trailing ASCII spaces before CHECK;
-- checks cannot inspect that lost input. Column types are unchanged here.
ALTER TABLE public.stations
    DROP CONSTRAINT chk_stations_provider,
    DROP CONSTRAINT chk_stations_indicativo,
    ADD CONSTRAINT chk_stations_provider
        CHECK (
            provider <> ''
            AND provider = BTRIM(provider,
                U&'\0009\000A\000B\000C\000D\001C\001D\001E\001F\0020\1680\2000\2001\2002\2003\2004\2005\2006\2008\2009\200A\2028\2029\205F\3000')
            AND provider = UPPER(provider)
        ),
    ADD CONSTRAINT chk_stations_indicativo
        CHECK (
            indicativo <> ''
            AND indicativo = BTRIM(indicativo,
                U&'\0009\000A\000B\000C\000D\001C\001D\001E\001F\0020\1680\2000\2001\2002\2003\2004\2005\2006\2008\2009\200A\2028\2029\205F\3000')
        );

-- ADD CHECK validates existing rows (no NOT VALID). Any failure aborts the
-- transaction, restoring both previous constraints without modifying rows.
COMMIT;
