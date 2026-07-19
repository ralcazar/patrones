# Escenarios de prueba de carga

Cada fichero `.yml` de esta carpeta define un escenario completo y
autocontenido: el lanzador (`./gradlew pruebaCarga -Pescenario=<nombre>`)
lee el fichero y no acepta ningún parámetro suelto adicional, de forma que
**el fichero es la única fuente de verdad** y el escenario se puede
reproducir fielmente pasado el tiempo.

## Esquema

```yaml
nombre: <slug, debe coincidir con el nombre del fichero>
descripcion: <qué pregunta responde este escenario>

# Reproducibilidad: semilla de los generadores aleatorios de los mocks
# (fallos, latencias, retrasos Kafka). Cada pod deriva la suya como
# semilla + índice de pod. OJO: fija la SECUENCIA de fallos/latencias,
# no el entrelazado de hilos: dos ejecuciones son estadísticamente
# equivalentes, no idénticas instrucción a instrucción.
semilla: 42

duracion: PT10M          # ISO-8601; tiempo de inyección + drenaje
drenaje-maximo: PT5M     # tras la duración se deja de inyectar y se espera
                         # hasta esto a que las órdenes vivas terminen

pods: 4                  # contextos Spring simultáneos contra la misma H2

inyeccion:
  tramitaciones: 2000    # total de POST /tramitaciones a inyectar
  ritmo-por-segundo: 5   # ritmo sostenido de inyección

rest:                    # mocks de PuertoPaso1..8 y PuertoSagaSecundaria1/3
  latencia-ms: { min: 100, max: 400 }   # uniforme con la semilla
  tasa-fallo: 0.10       # probabilidad de RuntimeException por llamada
  # overrides opcionales por puerto, p. ej. un paso especialmente lento:
  # por-puerto:
  #   PuertoPaso4: { latencia-ms: { min: 800, max: 1500 }, tasa-fallo: 0.30 }

kafka:                   # simulador de la respuesta diferida de la secundaria 2
  retraso-ms: { min: 500, max: 5000 }
  tasa-perdida: 0.0      # respuestas que nunca llegan (fuerza conciliación/ticket)

motor:                   # se aplica como propiedades a CADA pod; lo que no
  planificador:          # se indique usa los valores de application.yml
    intervalo-ms: 1000
    trabajadores: 2
    lote: 16
  lease: PT10M
  cron:                  # opcional (fase 2): acelera los cron de producción,
                         # que si no una ejecución corta nunca los dispara
                         # (por defecto cada 3h/cada noche, ver application.yml).
                         # Los tres campos son opcionales; el que no se indique
                         # deja el valor por defecto de application.yml.
    tickets: "0 * * * * *"    # -> ordermanager.tickets.cron
    limpieza: "0 0 * * * *"   # -> ordermanager.limpieza.cron
    purga: "0 30 * * * *"     # -> sagas.purga-datos-negocio.cron
```

## Nota sobre `pods.log` y el timestamp

Cada línea de `pods.log` lleva un timestamp ISO-8601 delante del texto ya
estructurado (`2026-01-01T10:00:00.000+01:00 evento=... pod=...`), añadido
por `ConfiguradorLogging` (fase 2, `com.ejemplo.app.carga.logging`) como
prefijo de línea de Logback: NO es una clave `clave=valor` más (los puntos de
log de producción no lo llevan en el propio mensaje), es lo que el
analizador de la fase 3 necesita para calcular throughput por minuto y
similares sin que production tenga que anotar cada evento con su hora.

## Salidas de una ejecución

Cada ejecución escribe en `build/pruebaCarga/<nombre>-<timestamp>/`
(fuera del control de versiones):

- `pods.log` — log estructurado (una línea por evento, `clave=valor`, con `pod=N`)
- `bbdd.mv.db` — la H2 en fichero, consultable tras la prueba
- `informe.md` — el resumen del analizador (throughput, colisiones,
  estados finales, invariantes pasa/falla)
- `pods-compacto.log` — transformación 1:1 de `pods.log` (fase 5,
  `CompactadorLogLlm`) pensada para que un agente LLM lea el log de eventos
  completo dentro de su presupuesto de contexto: descarta las líneas que no
  son eventos, recorta el timestamp a la hora, abrevia el pod y sustituye
  cada UUID de orden por un alias corto
- `leyenda-compacto.md` — formato de línea de `pods-compacto.log` y la tabla
  de alias de orden -> UUID completo para volver a `pods.log`/la H2

## Matriz actual

| Escenario | Pregunta que responde |
|---|---|
| `humo` | ¿Funciona el harness de punta a punta? (corto, sin fallos) |
| `base-sin-fallos` | Línea base de throughput y ritmo de reclamo |
| `fallos-01` | ¿El 1% de fallo REST es ruido absorbido por los reintentos? |
| `fallos-10` | ¿Los reintentos degradan el throughput sin colapsar la cola? |
| `fallos-30` | Estrés: ¿la cola drena? ¿se abren los tickets que tocan? |
| `contencion-8-pods` | ¿Cuánta colisión optimista hay con muchos pods y poco lote? |
| `respuestas-perdidas` | ¿La conciliación de la secundaria 2 y los tickets reaccionan? |
| `humo-contencion` | Versión corta de `contencion-8-pods` (~5 min): ¿el harness registra y cuenta colisiones optimistas reales en un tiempo asumible? |
| `rafaga-extrema` | ¿Aparecen anomalías de entrelazado bajo concurrencia máxima? (log pequeño para análisis por agente LLM) |

Los escenarios comparables entre sí (`base-sin-fallos`, `fallos-*`)
mantienen idénticos todo lo demás (pods, carga, latencias, motor): solo
varía `tasa-fallo`, para que la comparación aísle esa variable.

## Catálogo de eventos del log

Fase 0 del plan de pruebas de carga: cada línea de `pods.log` es un evento,
formato `clave=valor` separado por espacios, siempre con `evento=<nombre>` y
`pod=<valor>` (identifica al pod que lo emitió; en producción real viene de
la propiedad `ordermanager.pod`, por defecto `local`). Este catálogo es el
contrato exacto que consume el analizador determinista (fase 3): nombres de
evento y de campo no cambian sin actualizar esta tabla y el analizador a la
vez.

La mayoría de eventos nacen en `business.ordermanager.aplicacion.servicio`
(`ServicioContinuarOrden`, `ServicioLimpiezaDatos`) a través del puerto
`PuertoObservadorEjecucion` (ver `order-manager/docs/17-clases-aplicacion-nucleo.puml`),
implementado por `AdaptadorObservadorLog`
(`infraestructure.ordermanager.eventos`, ver diagrama 24) con SLF4J. Tres
eventos nacen ya en infraestructura (adaptadores de entrada, o el propio
adaptador de tickets) y no pasan por ese puerto: `ticket_abierto`,
`tramitacion_creada` y `respuesta_secundaria2_registrada`; se loguean con el
mismo formato para que el analizador no tenga que distinguir el origen.

| Evento | Campos (además de `orden`, `tipo`, `pod` salvo que se indique) | Emitido desde | Cuándo |
|---|---|---|---|
| `reclamo_ganado` | — | `ServicioContinuarOrden.reclamarToken` | Un pod reclama el token con éxito (orden viva, sin token vigente, guardado sin conflicto) |
| `reclamo_perdido` | `motivo` = `TOKEN_VIGENTE` \| `NO_VIVA` \| `COLISION_OPTIMISTA` | `ServicioContinuarOrden.reclamarToken` / `reclamarYEjecutar` | El pod no consigue el token: la orden ya está finalizada, otro pod tiene un token vigente, o el guardado del reclamo choca por versión |
| `colision_optimista` | `operacion` = `reclamarToken` \| `ejecutarPaso` \| `programarReintento` | `ServicioContinuarOrden.reclamarYEjecutar` | Un guardado del agregado pierde el optimistic lock (otro actor escribió entre medias) en esa operación concreta |
| `paso_completado` | `duracion_ms` (medida con `System.nanoTime()` alrededor de `ProcesadorOrden.ejecutarPaso`) | `ServicioContinuarOrden.reclamarYEjecutar` | `ejecutarPaso` vuelve sin lanzar (independientemente de si la orden sigue, aparca o finaliza) |
| `paso_fallido` | `intento` (nº de fallo acumulado tras este), `error_tipo` (FQCN de la excepción), `error_mensaje` | `ServicioContinuarOrden.reclamarYEjecutar` | `ejecutarPaso` lanza una `RuntimeException` que no es de concurrencia optimista |
| `reintento_programado` | `intento`, `espera_ms` (según `PoliticaReintentos.esperaTras`) | `ServicioContinuarOrden.reclamarYEjecutar` | Tras un `paso_fallido`, el reintento se programa y persiste con éxito |
| `orden_aparcada` | `ventana_ms` | `ServicioContinuarOrden.reclamarYEjecutar` | El procesador devuelve `SenalPaso.Aparcar` (espera un evento externo) |
| `orden_finalizada` | `resultado=ok` | `ServicioContinuarOrden.reclamarYEjecutar` | El procesador devuelve `SenalPaso.Finalizada`; en el diseño actual del motor la finalización siempre es éxito (los fallos nunca agotan la escalera de reintentos, se repiten indefinidamente cada 180 min con ticket abierto) |
| `purga_datos_antiguos` | sin `orden`/`tipo` (evento agregado, no por orden): `ordenes_eliminadas`, `mensajes_eliminados` | `ServicioLimpiezaDatos.purgarAnterioresA` | Cada barrido de limpieza, con el recuento de filas purgadas de cada tipo |
| `ticket_abierto` | `external_id`, `intentos`, `error_tipo`, `error_mensaje` (`sin-registrar` si no hay error) | `AdaptadorTicketsLog.abrir` (infraestructura; no pasa por `PuertoObservadorEjecucion`) | Una línea por orden del barrido de tickets (`ServicioTicketsSoporte`), tras escribir el ticket |
| `tramitacion_creada` | `external_id` (`tipo` siempre `PRINCIPAL`) | `ControladorTramitaciones.iniciar` (infraestructura) | `POST /tramitaciones` crea el agregado inicial con éxito |
| `respuesta_secundaria2_registrada` | `exito` (boolean), `mensaje_id` (`tipo` siempre `SECUNDARIA2`) | `ConsumidorRespuestaSecundaria2.onRespuesta` (infraestructura) | Tras delegar en `CasoUsoRegistrarRespuestaSecundaria2`, tanto si la respuesta es de éxito como de error |

Notas para el analizador:

- `orden` es el UUID de `OrdenId`; `tipo` es el valor de `TipoOrden`
  (`PRINCIPAL`, `SECUNDARIA1`, `SECUNDARIA2`, `SECUNDARIA3`).
- Un `colision_optimista` con `operacion=reclamarToken` siempre viene
  acompañado de un `reclamo_perdido` con `motivo=COLISION_OPTIMISTA` para la
  misma orden (mismo pod, misma línea de tiempo).
- Un `paso_fallido` no implica `reintento_programado`: si el guardado del
  reintento también choca por versión, en su lugar se emite
  `colision_optimista` con `operacion=programarReintento` y la excepción se
  propaga (el pod se retira sin programar nada; el siguiente barrido del
  planificador la recogerá como candidata otra vez).
