# Validación de la integración con AEMET

La integración valida de forma local el inventario de estaciones y no descarga datos meteorológicos. La estación 7211B se conserva como ejercicio de integración; no representa una elección científica.

## Flujo seguro

1. El runner solicita el inventario a la URL fija y envía `api_key` únicamente en esa petición.
2. Interpreta el JSON del sobre y distingue el estado HTTP de `estado` (estado de aplicación de AEMET).
3. Antes de la segunda petición valida que `datos` sea una URI HTTPS cuyo host sea exactamente `opendata.aemet.es`, sin información de usuario y con puerto ausente o 443.
4. Solicita el recurso validado sin reenviar la clave, y valida JSON, array no vacío, objetos e `indicativo` debe coincidir con STATION_ID, ser no nulo, textual y no blanco.

Los mensajes de error indican la etapa y categoría —HTTP, transporte, JSON, estructura o validación— sin conservar causas de transporte/HTTP ni incluir cuerpos, credenciales o URI recibidas. La salida estándar no incluye respuestas. Se reutiliza un único `JsonMapper` por runner. El transporte JDK impone timeouts de conexión y lectura de dos segundos y nunca sigue redirecciones.

## Contratos y evidencia

| Requisito                                                                                                             | Prueba                                               | Evidencia observada                                                                                                                                                                                                                |
| --------------------------------------------------------------------------------------------------------------------- | ---------------------------------------------------- | ---------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| Estado de aplicación distinto del HTTP; sobre malformado o estructuralmente inválido                                  | `AemetRunnerTest`                                    | RED observado antes de implementar: estado de aplicación devolvía el mensaje genérico; la suite actual de 41 pruebas pasa.                                                                                                         |
| Respuestas vacías/nulas y rechazo explícito de `[]`                                                                   | `AemetRunnerTest`                                    | RED observado: cuerpo ausente se clasificaba incorrectamente y `[]` se aceptaba; la suite actual de 41 pruebas pasa.                                                                                                               |
| HTTP 4xx/5xx por etapa y errores de transporte saneados                                                               | `AemetRunnerTest`                                    | RED observado para los dos HTTP 500; la suite actual de 41 pruebas pasa. Se verifican estado, causa ausente y no filtración.                                                                                                       |
| Casos de sobre: array/escalar; estado ausente, nulo o textual; `200` entero                                           | Regresiones ya verdes de `AemetRunnerTest`           | Los casos nuevos se añadieron después de implementar el comportamiento y pasan; no se presenta RED retrospectivo. El estado ausente ya tenía cobertura anterior.                                                                   |
| Fallo de transporte en la segunda etapa                                                                               | Regresión ya verde de `AemetRunnerTest`              | Respuesta HTTP simulada en la primera petición y excepción local en la segunda; mensaje indica etapa `data`, no conserva causa ni detalle privado. No se observó RED retrospectivo.                                                |
| Cuerpos nulos/vacíos/blancos en ambas peticiones                                                                      | Regresiones ya verdes de `AemetRunnerTest`           | Casos cubren nulo, cadena vacía y solo espacios; no se observó RED individual para los casos agregados después de implementar el guard.                                                                                            |
| Timeout finito, sin seguir redirección, transporte loopback aislado                                                   | `AemetClientConfigurationTest`, `AemetTransportTest` | 1 prueba de configuración y 3 pruebas loopback pasan; la lectura lenta expira dentro del límite de cinco segundos. Estas pruebas se añadieron cuando la configuración ya estaba implementada; no se observó RED previo para ellas. |
| Aislamiento del contexto Spring                                                                                       | `WeatherApplicationTests`                            | 1 prueba pasa con `spring.config.import=`, clave sintética y `AemetRunner` sustituido por mock; Spring Boot ejecuta `CommandLineRunner` durante el arranque del contexto.                                                          |
| Contratos previos de allowlist, cabecera inicial, ausencia de clave en segunda llamada y verify del servidor simulado | Regresiones existentes de `AemetRunnerTest`          | 21 pruebas ya pasaban en la línea base observada por el padre; las 41 pruebas actuales del runner pasan, incluidas las anteriores.                                                                                                 |

El primer RED de validación tuvo seis fallos que mostraron brechas reales: estado de aplicación genérico, cuerpo inicial ausente, cuerpo final blanco, aceptación de `[]` y HTTP 500 en ambas etapas. Un séptimo fallo no fue una brecha de implementación: el fixture de `indicativo` contenía un tabulador literal y no era JSON; se corrigió a `\\t`. Los casos adicionales de esta actualización son regresiones ya verdes, no RED retrospectivo. T1 permanece parcial porque no todos los casos tuvieron su propio RED observado.

### Incremento de validación — 2026-10-18

Se incorpora una comprobación de identidad de estación.

El cliente rechaza una respuesta cuyo `indicativo`
no coincide exactamente con la estación solicitada.

- Estación actual: 7211B.
- Test: shouldRejectStationDifferentFromRequestedOne.
- RED observado: se esperaba IllegalStateException,
  pero el runner aceptaba el identificador 9999X.
- GREEN observado: prueba individual superada.
- Regresión: AemetRunnerTest, 42/42 pruebas.
- Suite completa: 47/47 pruebas, BUILD SUCCESS.

Todas las pruebas se ejecutaron localmente con Maven
offline, respuestas simuladas y transporte loopback.

No se realizó adquisición meteorológica real.

## Ejecución offline

Desde este directorio, con dependencias ya presentes en caché:

```powershell
.\mvnw.cmd -o "-Dtest=AemetRunnerTest" test
.\mvnw.cmd -o "-Dtest=AemetClientConfigurationTest,AemetTransportTest" test
.\mvnw.cmd -o "-Dtest=WeatherApplicationTests" test
.\mvnw.cmd -o test
.\mvnw.cmd -o compile
.\mvnw.cmd -o javadoc:javadoc
```

## Resultados de esta validación

- Suite completa: 46 pruebas, cero fallos, errores o saltos; BUILD SUCCESS.
- Compilación: BUILD SUCCESS.
- Suite de runner: 41 pruebas, cero fallos, errores o saltos.
- Configuración y transporte: 4 pruebas; contexto: 1 prueba.
- Javadoc Maven: `maven-javadoc-plugin:3.12.0` no estaba en la caché y el modo offline impidió resolverlo; el goal sigue sin verificarse. Como alternativa local, JDK Javadoc generó `target/javadoc-check/index.html` y las tres páginas de clases de producción con 10 advertencias de documentación (campos y miembros de `WeatherApplication` sin comentarios); el verifier independiente había reportado 12 antes de esta corrida. La classpath se extrajo en memoria exclusivamente de `java.class.path` del XML de Surefire exitoso; no se enumeraron otras propiedades ni se mostró la classpath.
- Avisos observados en el contexto: Mockito activa su mock-maker inline con auto-attach y la JVM advierte sobre carga dinámica del agente Byte Buddy; no afectan al resultado de la suite.
- Límites de transporte: se comprobó la configuración del timeout de conexión, pero una conexión rechazada no demuestra su expiración. La respuesta lenta en loopback demuestra el timeout de lectura del escenario probado, no todos los patrones posibles de entrega lenta del cuerpo. `MockRestServiceServer` no demuestra por sí mismo el comportamiento de una conexión real.
- En la suite final, todas las respuestas del runner son simuladas y los transportes son loopback; no se descargaron archivos meteorológicos ni se usaron fixtures de AEMET. La llamada real accidental previa está detallada en la nota siguiente.

## Nota de aislamiento de contexto

La primera ejecución focalizada de `WeatherApplicationTests`, antes de añadir el mock del runner, mostró que `@SpringBootTest` ejecuta el `CommandLineRunner`: se realizó una petición accidental al endpoint real de inventario con la clave sintética `test-only-placeholder`, que respondió HTTP 401. Después, una comprobación de propiedad sin clave sintética heredó una propiedad sensible del entorno y la incluyó en el mensaje de aserción fallida; el valor se omite aquí y no se conserva. Esa ejecución no realizó una petición, porque el runner ya estaba sustituido por mock. No se inspeccionó `.env`. El test fija ahora `spring.config.import=`, sobrescribe la propiedad con una clave sintética y reemplaza el runner con `@MockitoBean`; las ejecuciones posteriores pasaron sin petición externa. No ejecutar este contexto sin el mock ni sin la propiedad sintética.

No hay afirmación de integración real aprobada: la suite final usa respuestas HTTP simuladas y loopback. El assessment nativo no fue evaluable por el estado completamente no rastreado del proyecto; la verificación independiente se manejó de forma conservadora. RDD sigue apagado y no se hicieron commits.

## Comando Javadoc local

Se comprobó que `target` y `target/surefire-reports/TEST-com.glea.weather.AemetRunnerTest.xml` existían antes de generar salida. En PowerShell, extraer solo `java.class.path` (sin imprimirlo) y generar las páginas:

```powershell
if (!(Test-Path -LiteralPath "target")) { throw "Maven target directory is missing." }
if (!(Test-Path -LiteralPath "target\surefire-reports\TEST-com.glea.weather.AemetRunnerTest.xml")) { throw "Successful runner Surefire XML is missing." }
$report = Select-Xml -Path "target\surefire-reports\TEST-com.glea.weather.AemetRunnerTest.xml" -XPath "/testsuite/properties/property[@name='java.class.path']"
$classpath = $report.Node.GetAttribute("value")
if ([string]::IsNullOrWhiteSpace($classpath)) { throw "Successful runner Surefire XML has no java.class.path property." }
& javadoc -private -encoding UTF-8 -docencoding UTF-8 -charset UTF-8 -notimestamp -d "target\javadoc-check" -classpath $classpath -sourcepath "src\main\java" -subpackages "com.glea.weather"
```
