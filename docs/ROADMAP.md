# Roadmap operativo de Glea Weather

Fecha de revisión: 1 de noviembre de 2026. Objetivo: llegar a una comparación reproducible de Tmin/Tmax con y sin precipitación, no construir una plataforma meteorológica en producción.

Este documento orienta el siguiente trabajo; **no sustituye los contratos aprobados ni declara cerrados los gates**. La revisión fue de código y documentación local. No se accedió a credenciales, AEMET ni PostgreSQL real.

## Ruta corta

1. Recuperar una ejecución verificable de las pruebas locales.
2. Cerrar la representación física mínima de G2 y completar una adquisición con datos de prueba.
3. Ejecutar una descarga real autorizada, auditar el corpus y construir M0.
4. Comparar M1/M2, evaluar el test reservado y preparar la entrega.

Investigar los intervalos físicos de AEMET y trabajar en la memoria en paralelo desde ahora. No ampliar interfaces ni familias de modelos antes de obtener el primer resultado válido.

## Punto de partida comprobado

| Componente | Estado observado | Qué no demuestra todavía |
|---|---|---|
| `AemetRunner` | Consulta y valida inventario; resuelve la estación en PostgreSQL mediante `StationRepository`. | Descarga de climatologías diarias. |
| `StationRepository` | Búsqueda e inserción parametrizadas, con validación compartida. | Un pipeline completo de adquisición. |
| `AcquisitionRun` y su repositorio | Contrato de cinco campos e inserción JDBC; sin llamada desde el flujo de arranque/descarga. | Estados finales, referencias RAW, registros meteorológicos o recuperación. |
| SQL de ejecuciones | Identidad, estación, rango solicitado e instante de petición. | Cierre de G2. |
| Python | `main.py` es una consulta exploratoria del inventario con acceso a red. | Auditoría del corpus, variables, modelos o evaluación. |
| Pruebas | Hay pruebas unitarias, HTTP local y PostgreSQL optativo. | No se ha obtenido un resultado actual de la suite en esta revisión. |

La guía declara T1/G1 cerrado documentalmente; esto no equivale a ejecución. La alineación registrada en `odd/tasks/ad-t2-alignment.md` tampoco acredita implementación de G2.

### Fuentes de navegación

- [Guía canónica](GLEA-WEATHER-DEVELOPMENT-GUIDE.md): alcance (§4.1), persistencia mínima y recuperación (§«Persistencia técnica mínima»), siguiente paso (§19).
- [Plan operativo referenciado por la guía](GLEA-WEATHER-PLAN.md): T1–T9. También existe `GLEA-WEATHER-PLAN-REVISADO.md`; este roadmap no cambia su autoridad ni los fusiona.
- [Pruebas de estaciones](../weather/docs/STATION-JDBC-TEST.md) y [pruebas de ejecuciones](../weather/docs/ACQUISITION-RUN-JDBC-TEST.md).
- La copia de la guía en `weather/docs` coincide con la canónica en esta revisión. No crear otra copia de este roadmap.
- Los archivos `H0-DATA.md` y `KEY-METRICS.md` del directorio superior tratan BOE/MultiEURLEX: no son evidencia meteorológica.

## Trabajo ordenado por resultados

Las fechas son una propuesta de planificación, no una estimación garantizada: falta conocer la disponibilidad de trabajo y resolver la ejecución de pruebas. Si se retrasa un hito, reducir primero ampliaciones opcionales, nunca la validez de la evaluación.

| Paso y ventana propuesta | Acción y entregable | Criterio de cierre | Dependencia |
|---|---|---|---|
| **0. Base verificable — 1–3 nov.** | Preservar cambios locales; resolver el fallo de Maven y ejecutar la suite offline. Dejar un registro corto de resultados y pruebas omitidas. | Compilación y pruebas locales observadas; ninguna integración real declarada como superada por estar desactivada. | Ninguna. |
| **1. Acotar G2 — 3–5 nov.** | Elegir una representación SQL mínima que permita seguir estación → ejecución → RAW → registro técnico. Concretar campos/estados y consulta de cobertura conforme a la guía. | Una decisión breve implementable, sin preguntas abiertas de representación; mantiene recuperación e integridad aprobadas. | Leer contratos vigentes; paso 0 para verificar implementación. |
| **2. Recorrido técnico completo — 5–10 nov.** | Implementar con fixtures: ejecución inicial, bytes RAW, hash/manifiesto, persistencia técnica, cierre y recuperación manual. | Evidencia del flujo correcto, rollback, RAW conservado al fallar BD, repetición del mismo run sin duplicados y rechazo de discrepancias; PostgreSQL real en entorno de prueba autorizado para cerrar G2. | Paso 1. |
| **3. Corpus identificable — 10–13 nov.** | Piloto de intervalo corto y después descarga del periodo previsto. Contrastar contrato del endpoint, límites y respuestas. | Originales verificables, procedencia, petición y resultado diferenciados, sin credenciales en artefactos. G3 acreditado. | G2 y autorización explícita para API/BD. |
| **4. Auditoría independiente — 13–17 nov.** | Informe Python reproducible de calendario, duplicados, ausencias, códigos especiales, cobertura y denominadores por variable. | G4 documentado; cifras obtenidas del corpus actual, no copiadas de exploraciones antiguas. Decisiones de tratamiento explícitas. | Corpus del paso 3. |
| **5. Dataset causal y baseline — 17–21 nov.** | Cerrar semántica temporal, generar retardos por calendario, separar por fecha objetivo y obtener M0. | G5/G6 acreditados para las variables usadas; pruebas de huecos y fronteras temporales, transformaciones ajustadas solo en entrenamiento y métricas reproducibles de M0. | G4 y semántica física verificada. |
| **6. Comparación acotada — 21–27 nov.** | Una familia inicial de regresión, M1 térmico y M2 con lluvia, mismo protocolo y fechas comunes. Seleccionar usando validación. | Resultados de validación por objetivo y N; variables/retardos/hiperparámetros fijados sin consultar errores de test. G7. | Paso 5; semántica de lluvia para M2. |
| **7. Evaluación final — 27–30 nov.** | Congelar protocolo y evaluar el test reservado sin usarlo para reajustar. Guardar predicciones, métricas y gráficos. | MAE, RMSE, diferencias de error y cobertura por objetivo; al menos tres visualizaciones útiles y limitaciones. G8. | G7. |
| **8. Entrega reproducible — 1–7 dic.** | Probar instalación y recorrido de demostración; terminar manuales, memoria propia, diapositivas y vídeo. | Otra ejecución con los artefactos entregados reproduce el recorrido; revisión del paquete y accesos. | Trabajo documental continuo; resultados para conclusiones. |
| **Reserva — 8–10 dic.** | Corregir problemas de empaquetado y ensayar/subir la entrega. | Archivos completos y accesibles antes del límite del 11 dic., 09:59 peninsular. | Paso 8. |

## Qué puede avanzar en paralelo

- **Semántica AEMET desde ahora:** reunir fuentes primarias sobre etiqueta, inicio/fin de observación y zona horaria de Tmin, Tmax y precipitación. Fijar el corte retrospectivo a partir de esa evidencia, no de desplazamientos convenientes.
- **Sin lluvia verificada:** avanzar solo en las variables térmicas cuya semántica esté acreditada. M0/M1 también requieren corpus auditado y dataset válido; no quedan autorizados automáticamente por aplazar M2.
- **Alcance retrospectivo:** verificar intervalos físicos no equivale a demostrar disponibilidad operativa de publicación. Esta última no es requisito del experimento actual; no prometer servicio en vivo.
- **Memoria desde ahora:** completar estado del arte, Gantt estimado/real, DAFO, riesgos y decisiones aplicadas. Añadir resultados cuando existan; redactar conclusiones al final. Seguir plantilla y restricciones de autoría de la normativa.

## Simplificar sin romper lo aprobado

**Mantener:** Java/PostgreSQL existente; validación de entradas y destinos; protección de secretos; RAW íntegro; recuperación ya acordada; controles temporales; comparación pareada y test reservado.

**No añadir al mínimo:** Angular, servicio continuo, alertas, reintentos automáticos, scheduler, ORM, clúster, más estaciones, índices preventivos o varias familias de modelos. No reescribir todo en Python para ahorrar una complejidad que ya está implementada parcialmente.

**Decisión pendiente, no cambio autorizado:** si la recuperación y los estados aprobados impiden el calendario, proponer una reducción concreta y valorar sus pérdidas de trazabilidad/recuperación. Solo tras aceptación actualizar objetivos, guía y planes de forma coherente. Hasta entonces sigue vigente G2 completo; este roadmap no lo rebaja.

La guía permite elegir el número mínimo de tablas y no obliga a JSONB, cuatro tablas ni un número fijo de pruebas. Simplificar la representación es compatible con conservar el comportamiento exigido.

## Precauciones para la siguiente implementación

- Reutilizar `StationValidator` y `StationRepository`; no duplicar validación ni resolución de estaciones.
- `AcquisitionRun` exige precisión compatible con microsegundos: el futuro llamador debe construir el instante conforme al contrato.
- La descarga actual del inventario devuelve texto. Para RAW diario conservar los bytes originales, no serializar de nuevo un JSON interpretado.
- El rango pedido y la cobertura recibida son conceptos distintos. Preservar posiciones, fechas repetidas y valores especiales; la limpieza corresponde a Python.
- No ejecutar `main.py` como prueba offline: hace llamadas de red.
- No reutilizar credenciales o sesiones ni abrir conexiones reales sin autorización del destino, operación y credencial/sesión.

## Verificación de esta revisión

Se inspeccionaron código, SQL, pruebas y guías, incluyendo cambios locales no confirmados. No se modificaron esos archivos ni se ejecutó la aplicación.

Comando intentado desde `weather`:

```powershell
.\mvnw.cmd -o "-Dacquisition.runs.postgres.test=false" "-Dstations.postgres.test=false" test
```

Resultado: salida 1 antes de ejecutar pruebas. Maven no pudo abrir el canal de un archivo `.lock` de su repositorio local al resolver el parent de Spring Boot. La causa última de acceso/bloqueo no se determinó; no se borraron locks ni se cambiaron dependencias. **No hay evidencia de suite verde en esta revisión.** Integraciones PostgreSQL y AEMET no ejecutadas.

## Siguiente acción concreta

Resolver la ejecución offline sin alterar la aplicación y fijar el contrato físico mínimo de G2. Después implementar una adquisición completa con fixtures, antes de ampliar arquitectura. En paralelo, preparar evidencia temporal AEMET y los apartados académicos pendientes.
