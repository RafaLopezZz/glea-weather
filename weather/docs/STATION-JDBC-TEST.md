# Verify station persistence against PostgreSQL

The real test is opt-in and has not been executed as part of this change.
Run it only against `gleaweather_test`, with the current `public.stations`
schema already applied by its owner. This procedure does not apply migrations.

## Run manually

Open a dedicated PowerShell window in the `weather` module. Adjust the host,
port and database user below. Do not put a password in the command or a file.
The password is requested interactively, temporarily passed to the Maven child
process through its environment, and removed afterwards.

```powershell
$env:GLEAWEATHER_TEST_JDBC_URL = 'jdbc:postgresql://localhost:5432/gleaweather_test'
$env:GLEAWEATHER_TEST_DB_USER = Read-Host 'PostgreSQL test user'
$password = Read-Host 'PostgreSQL password' -AsSecureString
try {
    $env:GLEAWEATHER_TEST_DB_PASSWORD = [System.Net.NetworkCredential]::new('', $password).Password
    .\mvnw.cmd -o "-Dtest=StationRepositoryPostgresTest" "-Dstations.postgres.test=true" test
    if ($LASTEXITCODE -ne 0) { throw 'PostgreSQL test failed; inspect the Maven output' }
} finally {
    Remove-Item Env:\GLEAWEATHER_TEST_DB_PASSWORD -ErrorAction SilentlyContinue
    $password.Dispose()
}
```

Expect one executed test with zero failures, errors or skips. A skipped test
is not evidence of PostgreSQL correctness. Missing credentials or an incorrect
database cause failure when the opt-in flag is enabled.

## What the test proves

- The real repository inserts and reads back lowercase, mixed-case and
  leading-zero identifiers exactly, including an optional name and a name
  containing SQL punctuation.
- PostgreSQL rejects duplicate `(provider, indicativo)` values; the test checks
  that one row remains and that its name was not updated.
- Savepoints recover from duplicate errors so the transaction can continue.
  A final rollback removes test rows, even on assertion failure. PostgreSQL
  identity sequences still advance and are not restored by rollback.

`StationRepositoryTest` uses Mockito, not PostgreSQL: it checks validation before
any JDBC interaction, parameter binding, returned ids and propagation of a
simulated duplicate error. The normal `.\mvnw.cmd -o test` suite skips the real
test unless explicitly enabled.

## G2 block 1: application wiring

`StationConfiguration` now supplies `DataSource`, `JdbcClient` and
`StationRepository`. It uses the installed Spring JDBC dependency without an ORM,
connection pool or automatic schema initialization. Creating these beans does not
open a database connection. G2 remains open.

Supply these properties externally (Spring environment variables are shown):

| Variable | Purpose |
| --- | --- |
| `SPRING_DATASOURCE_URL` | PostgreSQL JDBC URL for the intended database |
| `SPRING_DATASOURCE_USERNAME` | Database user |
| `SPRING_DATASOURCE_PASSWORD` | Database password |
| `AEMET_API_KEY` | AEMET inventory credential |

No defaults or credentials are supplied by the JDBC configuration. Use environment
variables in a dedicated terminal or the existing external Spring configuration;
never put secrets in commands, version control or logs. Normal Spring tests disable
the optional `.env` import and use dummy JDBC properties and a non-existent driver.

**Starting the application can write to the catalogue.** The existing
`CommandLineRunner` runs automatically on every application start. After both HTTP
requests and validation of the entire downloaded array, it resolves
`(AEMET, 7211B)`: reuse the existing id without changing any column, or insert the
first entry's exact optional name and return the generated id. All entries must
refer to the requested station and have a valid optional name. Database exceptions
escape the runner; an HTTP error handler does not intercept them. There is no retry,
upsert, concurrent-import protection or acquisition work in this block.

## Pending real end-to-end check (not executed)

This requires explicit operator authorization, valid AEMET credentials and a
disposable PostgreSQL database with the existing schema installed by its owner.
The repository-only real test above does **not** prove the AEMET-to-catalogue flow.

1. In a dedicated terminal, supply the four application variables above through
   your approved secret-entry mechanism. Do not reuse the test-only variable names.
   Override `SPRING_CONFIG_IMPORT` with `optional:classpath:/g2-no-external-file.properties`
   if you want to prevent the existing optional `.env` import during this check.
2. Check the target database and capture this query's result using your database
   client; do not delete or edit a pre-existing station to manufacture a test:
   ```sql
   SELECT id, provider, indicativo, nombre
   FROM public.stations WHERE provider = 'AEMET' AND indicativo = '7211B';
   ```
3. Only when ready to permit writes, run from the `weather` module:
   ```powershell
   .\mvnw.cmd -o "-DskipTests" spring-boot:run
   if ($LASTEXITCODE -ne 0) { throw 'Application failed; do not report success' }
   ```
   This command has not been verified here. If the Maven run plugin is not cached,
   offline execution fails; resolve that separately rather than silently going online.
4. Repeat the query. If initially absent, expect exactly one row with a generated
   id and the first inventory entry's unmodified name (or SQL NULL). Compare with
   the real inventory in an approved client without logging credentials or URLs.
5. Start the application again, then repeat the query. Expect the same id and all
   original column values. If a station already existed before step 3, this is the
   existing-station case; the missing-station case remains pending until a separate
   disposable database starts without that station. A differing remote name must
   not overwrite the stored name.
6. Record each result separately: simulated HTTP/JDBC, real repository-only test,
   and real AEMET + PostgreSQL application run. Remove secrets from the terminal
   environment afterwards. Closing or restarting an application does not undo an
   inserted station; cleanup is an explicit database-owner action.

Offline coverage uses `MockRestServiceServer` and Mockito for the runner and an
unusable JDBC URL for Spring wiring. It proves control flow and construction,
not PostgreSQL behavior, real credentials, remote payloads or connectivity.
