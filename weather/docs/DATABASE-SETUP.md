# Base de datos: preparación y migraciones

Los scripts se aplican **a mano, una sola vez y en orden** sobre la base de datos de PostgreSQL (`gleaweather_test` para pruebas). No hay herramienta de migraciones: el número del fichero marca el orden.

| Orden | Script | Qué hace |
|---|---|---|
| 1 | `src/main/resources/db/001-stations.sql` | Catálogo de estaciones (`stations`) |
| 2 | `src/main/resources/db/002-stations-whitespace.sql` | Endurece la validación de espacios en `provider` e `indicativo` |
| 3 | `src/main/resources/db/003-acquisition-runs.sql` | Registro de ejecuciones (`acquisition_runs`) |
| 4 | `src/main/resources/db/004-acquisition-results-and-records.sql` | Resultado y persistencia de cada run, ficheros originales (`raw_files`) y registros técnicos (`weather_records`) |
| 5 | `src/main/resources/db/005-fix-persisted-requires-complete-result.sql` | Corrige un hueco con NULL de la 004: un run sin resultado no puede marcarse como persistido |

## Requisitos
- PostgreSQL 16 o superior con `psql` en el PATH.
- Una base de datos vacía creada por su propietario: `CREATE DATABASE gleaweather_test;`

## Aplicar (PowerShell, desde `weather`)

```powershell
Get-Content .env | ForEach-Object {
    if ($_ -match '^SPRING_DATASOURCE_USERNAME=(.*)$') { $env:PGUSER = $Matches[1].Trim() }
    if ($_ -match '^SPRING_DATASOURCE_PASSWORD=(.*)$') { $env:PGPASSWORD = $Matches[1].Trim() }
}
Get-ChildItem src/main/resources/db/*.sql | Sort-Object Name | ForEach-Object {
    psql -X -h localhost -p 5432 -d gleaweather_test -v ON_ERROR_STOP=1 -f $_.FullName
    if ($LASTEXITCODE -ne 0) { throw "Falló $($_.Name)" }
}
Remove-Item Env:\PGPASSWORD
```

## Pruebas de contrato SQL
Cada prueba se ejecuta dentro de una transacción que termina en `ROLLBACK`, así que no deja datos.

| Prueba | Resultado esperado |
|---|---|
| `src/test/resources/sql/stations-contract.sql` | Solo líneas `PASS` y `ROLLBACK` |
| `src/test/resources/sql/acquisition-run-contract.sql` | 12 `PASS` y `ROLLBACK` |
| `src/test/resources/sql/acquisition-results-and-records-contract.sql` | 26 `PASS` y `ROLLBACK` (cubre 004 y 005) |

Evidencias en `docs/evidence/`.
