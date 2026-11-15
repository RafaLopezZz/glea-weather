# Verify the minimum acquisition-run registration contract

This increment implements only identity, station relationship, requested daily
period and explicit request time (AD-T2-12, AD-T2-13, AD-T2-15 and the identity row
of the normative matrix in [the development guide](../../docs/GLEA-WEATHER-DEVELOPMENT-GUIDE.md)).
It does not close G2 or implement the full acquisition contract. Test rows are
fixtures, not evidence of a meteorological acquisition. No download or startup
path calls this repository; the existing inventory flow is unchanged.

## Offline verification

Run from `weather`, without starting the application:

```powershell
.\mvnw.cmd -o "-Dtest=AcquisitionRunRepositoryTest,AcquisitionRunRepositoryPostgresTest" "-Dacquisition.runs.postgres.test=false" "-Dstations.postgres.test=false" test
.\mvnw.cmd -o "-Dacquisition.runs.postgres.test=false" "-Dstations.postgres.test=false" test
```

The focused command must execute 13 unit cases successfully and skip the one
real PostgreSQL test. The full suite must also skip the existing station
PostgreSQL test. A skipped test is not proof of PostgreSQL correctness.

## Data flow

The caller generates `UUID.randomUUID()` for a new run, supplies the existing
station id, two `LocalDate` bounds and the explicitly observed `Instant`, and
constructs `AcquisitionRun`. Its constructor rejects missing fields and empty or
inverted periods before any JDBC access. A single day is `[day, day.plusDays(1))`;
this says nothing about observed coverage.

`requestedAt` must have at most microsecond precision: PostgreSQL `TIMESTAMPTZ`
cannot preserve finer fractions. The constructor rejects finer precision rather
than silently rounding it; neither model nor repository truncates or replaces it.
See [PostgreSQL date/time resolution](https://www.postgresql.org/docs/current/datatype-datetime.html).

`AcquisitionRunRepository.save(run)` rejects a null record and executes one
parameterized `INSERT`, returning the affected-row count (expected: 1). It binds
the caller's UUID unchanged and converts `Instant` to an equivalent UTC
`OffsetDateTime` only at the JDBC boundary, as required by
[pgJDBC](https://jdbc.postgresql.org/documentation/query/#using-java-8-date-and-time-classes).
No prequeries, new UUID, current clock, retries or upsert are involved.
PostgreSQL remains responsible for PK/FK enforcement; its exceptions propagate.
The repository bean does not write merely by being constructed.

## Manual PostgreSQL checks (pending, not executed here)

Use only `gleaweather_test`, with the existing station schema and
`003-acquisition-runs.sql` already applied by the database owner. **Do not reapply
or edit migration 003.** Neither procedure applies migrations or starts Spring
Boot/AEMET. Use a dedicated PowerShell terminal in `weather`.

### SQL constraints

Adjust host and port; enter the user and password interactively:

```powershell
$dbUser = Read-Host 'PostgreSQL test user'
psql -X -W --host localhost --port 5432 --username $dbUser --dbname gleaweather_test --file src/test/resources/sql/acquisition-run-contract.sql
if ($LASTEXITCODE -ne 0) { throw 'SQL contract test failed; inspect the output' }
```

Expect 12 `PASS` notices and a final `ROLLBACK`:

| Input | Expected assertion |
| --- | --- |
| Original valid month | All five values exist unchanged |
| Equal / inverted bounds | `check_violation`, separately |
| Repeated UUID | `unique_violation` |
| Each of the five fields null, separately | `not_null_violation` |
| Fixture station inserted then removed | `foreign_key_violation` on the run insert |
| Two UUIDs, same station and one-day period | Both rows exist with exact bounds and timestamp, including microseconds |

The original four tests are retained. Their previously reported PASS results are
historical user evidence, not execution of this expanded script. In particular,
the null-station test is now implemented but still awaits real execution.
If fixed fixture identifiers already exist, stop and investigate; do not delete
or overwrite existing data to force a pass.

### Real JDBC round trip

```powershell
$env:GLEAWEATHER_TEST_JDBC_URL = 'jdbc:postgresql://localhost:5432/gleaweather_test'
$env:GLEAWEATHER_TEST_DB_USER = Read-Host 'PostgreSQL test user'
$password = Read-Host 'PostgreSQL password' -AsSecureString
try {
    $env:GLEAWEATHER_TEST_DB_PASSWORD = [System.Net.NetworkCredential]::new('', $password).Password
    .\mvnw.cmd -o "-Dtest=AcquisitionRunRepositoryPostgresTest" "-Dacquisition.runs.postgres.test=true" "-Dstations.postgres.test=false" test
    if ($LASTEXITCODE -ne 0) { throw 'PostgreSQL JDBC test failed; inspect the Maven output' }
} finally {
    Remove-Item Env:\GLEAWEATHER_TEST_DB_PASSWORD -ErrorAction SilentlyContinue
    $password.Dispose()
}
```

Expect one executed test, zero failures/errors/skips. It uses the real repository
to insert two Java-generated UUIDs for the same station and one-day period,
asserts one affected row per insert and record equality after reading all five
columns (including `Instant` microseconds). Duplicate and nonexistent-station
attempts must fail with SQLSTATE `23505` and `23503`, respectively. Savepoints
allow the transaction to continue; exactly two runs must remain before rollback.
The final rollback runs even after assertion failure. On success the test also
checks that its runs and station fixtures are absent afterwards.

Both procedures roll back their rows; PostgreSQL station identity sequences may
still advance. They do not alter existing station/run rows. Record SQL and JDBC
results separately. Real PostgreSQL verification remains pending until both
manual commands are executed successfully; no AEMET call is required.
