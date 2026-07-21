# Plan: sustituir la limpieza por dos purgas por tramitación

Artefacto de trabajo (borrar al terminar, como los planes históricos). Implementación
por **fases cortas**, cada una ejecutable por un subagente Sonnet y con `./gradlew check`
en verde al cerrarla. El coordinador (Opus) lanza un subagente por fase, en orden, y no
abre la siguiente hasta que la anterior está verde.

## Objetivo

Reemplazar el mecanismo de limpieza actual (por-orden + huérfanos en dos pasos) por
**dos casos de uso nuevos con criterio por tramitación** (grupo de las 4 sagas que
comparten `external_id`):

1. **Purga de adjuntos** — diaria **23:00**, corte **30 días**. Para cada `datos_negocio`
   cuyas 4 sagas están todas terminadas (bien o compensada) y cuya tramitación finalizó
   hace ≥30 días: pone a `NULL` el `contenido` de sus documentos y sella
   `datos_negocio.purgado_en`. **No borra filas.**
2. **Purga de completadas** — diaria **23:30**, corte **180 días**. Mismo criterio con 180
   días: **borra** `datos_negocio` + documentos **y** las 4 órdenes (+ satélites + auditoría).

En error: reintentar N veces y, si sigue fallando, **abrir incidencia** con el mecanismo
existente (log estructurado `evento=...` que recoge la herramienta externa).

## Decisiones ya tomadas (no re-preguntar)

- **Antigüedad** = `MAX(completada_en)` del grupo de órdenes del `external_id`. La
  tramitación cuenta como "terminada hace N días" cuando su última saga finalizó hace ≥N días.
- **Sello de purgado de adjuntos** = columna nueva `datos_negocio.purgado_en TIMESTAMP NULL`.
- `datos_negocio_documento.contenido` pasa a `NULL` permitido.
- Ambas purgas son **idempotentes** (NULL sobre NULL = no-op; borrar lo ya borrado = no-op),
  por eso reintentar el batch completo es seguro.

## Reglas del repo de obligado cumplimiento (CLAUDE.md)

- **`ordermanager ↛ sagas`**: el motor no importa nada de `business.sagas`/`infraestructure.sagas`
  ni nombra "saga". La orquestación que toca `datos_negocio` vive en `business.sagas`.
  Lo verifica `ReglasArquitecturaTest`.
- **Pureza business**: `business/**` solo Java + jMolecules (+ `jakarta.transaction.Transactional`
  en `aplicacion/**`). Nada de Spring/JPA/Jackson/Kafka en business.
- **Prohibido `ON DELETE CASCADE`** y cualquier borrado implícito: hijas antes que padre,
  explícito, en la misma transacción.
- **Adaptador de entrada nunca habla con uno de salida**: los `@Scheduled` invocan casos de uso.
- **100 % instrucción + rama** (`test` unitarios sin Spring + `integrationTest` H2 modo Oracle).
  Nada de Docker/Testcontainers/EmbeddedKafka. Cada cambio entra con sus tests.
- **Nomenclatura en español**, sin mezclar idiomas.
- **Diagramas sincronizados** en el mismo cambio (`order-manager/docs/`, `.puml` + `.png`
  con la skill `puml-to-png`) + índice `README.md`.
- Servicios de aplicación con reintento fuera de transacción se **auto-inyectan** (`self`,
  el proxy) para no perder `@Transactional` por auto-invocación; el `@Lazy`/registro `@Bean`
  vive en `infraestructure` (`ConfiguracionOrderManager` / `ConfiguracionSagas`).

---

## Fase 0 — Baseline (la hace el coordinador, sin subagente)

- Verificar árbol limpio y `./gradlew check` en verde.
- Commit baseline (mensaje: `Baseline antes de sustituir limpieza por purgas por tramitación`).
- Ampliar `git config http.postBuffer` si hay que empujar PNG (ver memoria del proyecto).

---

## Fase 1 — Esquema: `purgado_en` y `contenido` anulable

**Meta:** columna nueva + blob anulable, sin cambiar comportamiento todavía. Build verde.

Ficheros:
- `db/*.sql` (DDL Oracle aplicado a mano): añadir `datos_negocio.purgado_en TIMESTAMP NULL`;
  cambiar `datos_negocio_documento.contenido` a `NULL` permitido. Buscar el `.sql` real en `db/`.
- `infraestructure/sagas/datosnegocio/persistencia/DatosNegocioEntity.java`: campo
  `Instant purgadoEn` + `@Column(name="purgado_en")` (nullable) + getter; ajustar
  constructor y el mapeo en `AdaptadorDatosNegocio`.
- `DocumentoNegocioEntity.java`: `@Column(name="contenido", nullable=true)` (quitar `nullable=false`).
- Ajustar cualquier test/factoría que construya estas entidades.

Aceptación: `./gradlew check` verde (H2 `create-drop` genera el DDL nuevo; cobertura 100 %).

---

## Fase 2 — Motor: selección/purga por `external_id` + puerto de incidencias

**Meta:** capacidades genéricas nuevas en `ordermanager`, sin retirar aún lo viejo. Build verde.

Business (`business.ordermanager.aplicacion.puerto.salida.RepositorioOrden`):
- `List<ExternalId> externalIdsFinalizadosAntesDe(Instant corte)` — external_ids cuyas
  órdenes están **todas** terminadas y `MAX(completada_en) < corte`.
- `long purgarPorExternalIds(List<ExternalId> ids)` — borra órdenes de esos external_ids
  (auditoría → satélites → orden), reutilizando el patrón de `purgarFinalizadasAntesDe`.

Nuevo puerto de incidencias genérico (motor):
- `business.ordermanager.aplicacion.puerto.salida.PuertoIncidencias` con
  `void abrir(String tarea, String causa, int intentos)`.

Infra:
- `OrdenJpaRepository`: query nativa `externalIdsFinalizadosAntesDe`
  (`GROUP BY external_id HAVING COUNT(CASE WHEN completada_en IS NULL THEN 1 END)=0
  AND MAX(completada_en) < :corte`) y `borrarPorExternalIds`/reuso para la purga.
- `AdaptadorRepositorioOrden`: implementar los dos métodos nuevos (mapear `ExternalId`).
- `infraestructure.ordermanager.eventos.AdaptadorIncidenciasLog implements PuertoIncidencias`:
  `log.error("evento=incidencia_abierta tarea={} causa={} intentos={} pod={}", ...)`
  (mismo estilo que `AdaptadorTicketsLog`, con `${ordermanager.pod:local}`).
- Doble en memoria de `RepositorioOrden` en `src/test` (testsoporte): implementar los dos
  métodos nuevos con la MISMA semántica que la query nativa (agrupar por external_id).

Tests:
- Integración (`src/integrationTest`, H2 Oracle): `externalIdsFinalizadosAntesDe`
  (grupo con una viva → excluido; todas terminadas y viejas → incluido; frontera del corte)
  y `purgarPorExternalIds` (borra hijas antes que padre, no toca otros external_ids).
- Unitario: `AdaptadorIncidenciasLog` (formato del log) + doble en memoria.

Aceptación: `./gradlew check` verde, 100 % sobre el código nuevo, ArchUnit verde.

---

## Fase 3 — Sagas: los dos casos de uso + persistencia de adjuntos

**Meta:** casos de uso nuevos con reintento + incidencia. Build verde (lo viejo aún vive).

Business (`business.sagas`):
- `aplicacion.puerto.salida.RepositorioDatosNegocio`: nuevo
  `void purgarAdjuntos(DatosNegocioId id)` (contenido de documentos a NULL + sello
  `purgado_en`) y `List<DatosNegocioId> idsPorExternalIdsSinPurgar(List<ExternalId>)`
  o reutilizar `buscarPorExternalId` según convenga (evitar N+1: preferir una query por lote).
- `aplicacion.puerto.entrada.CasoUsoPurgarAdjuntos` / `CasoUsoPurgarCompletadas`.
- `aplicacion.servicio.comun.ServicioPurgarAdjuntos`:
  1. `motor.externalIdsFinalizadosAntesDe(now - 30d)`; 2. filtrar `datos_negocio` sin purgar;
  3. `purgarAdjuntos(id)`. `@Transactional`, idempotente.
- `aplicacion.servicio.comun.ServicioPurgarCompletadas`:
  1. `motor.externalIdsFinalizadosAntesDe(now - 180d)`; 2. `repoDatos.borrar(id)` de cada
  datos_negocio; 3. `motor.purgarPorExternalIds(...)`. Todo en **una** `@Transactional`.
- **Reintento + incidencia** (patrón `ServicioTicketsSoporte`): el método público NO es
  transaccional; hace `for i in 1..N { try self.ejecutar(); return } catch (RuntimeException e) { ultima=e }`
  y al agotar reintentos llama `PuertoIncidencias.abrir(tarea, causa, intentos)`. `self` es
  el proxy (auto-inyectado). Cada intento = su propia `@Transactional` vía `self`.
  Considerar un helper compartido de reintento operativo (distinto de `ReintentoOptimista`,
  que es para conflicto de versión) o replicar el patrón en cada servicio.

Infra (`infraestructure.sagas`):
- `AdaptadorDatosNegocio` + `DatosNegocioJpaRepository`/`DocumentoNegocioJpaRepository`:
  implementar `purgarAdjuntos` (`UPDATE datos_negocio_documento SET contenido=NULL WHERE datosnegocio_id=?`
  + set `purgado_en`) y la selección por lote de external_ids.
- Registro de los dos servicios como `@Bean` en `ConfiguracionSagas` con auto-inyección de `self`
  (mismo montaje que los `ServicioSaga*`/`ServicioContinuarOrden`).

Tests:
- Unitarios (`src/test`, dobles en memoria, sin Spring): selección por corte, idempotencia
  (segunda pasada no reprocesa purgados; borrar lo ya borrado no falla), y camino
  "falla N veces → `PuertoIncidencias.abrir` invocado con la causa correcta".
- Integración (H2 Oracle): `purgarAdjuntos` deja `contenido` NULL y `purgado_en` sellado
  conservando metadatos; `ServicioPurgarCompletadas` borra datos_negocio + documentos + órdenes
  en una tx.

Aceptación: `./gradlew check` verde, 100 %.

---

## Fase 4 — Planificadores (`@Scheduled`)

**Meta:** disparadores 23:00 / 23:30 cableados. El QUÉ ya está; aquí solo el CUÁNDO.

Infra (`infraestructure.sagas.datosnegocio.programados` o paquete análogo):
- `PlanificadorPurgaAdjuntos` — `@Scheduled(cron="${sagas.purga-adjuntos.cron:0 0 23 * * *}")`
  → `CasoUsoPurgarAdjuntos`. Retención configurable `@Value` (30 días por defecto), como
  `PlanificadorLimpieza`.
- `PlanificadorPurgaCompletadas` — `@Scheduled(cron="${sagas.purga-completadas.cron:0 30 23 * * *}")`
  → `CasoUsoPurgarCompletadas`. 180 días por defecto.
- Log resumen al terminar (nº de tramitaciones tocadas).

Tests: unitarios que verifican que el planificador delega en el caso de uso (mock/doble),
como los tests de los planificadores actuales.

Aceptación: `./gradlew check` verde, 100 %.

---

## Fase 5 — Retirar el mecanismo viejo

**Meta:** eliminar todo lo sustituido; 100 % y ArchUnit siguen verdes.

Eliminar (producción + sus tests):
- Motor: `ServicioLimpiezaDatos`, `CasoUsoLimpiarDatosAntiguos`, `PlanificadorLimpieza`,
  `RepositorioOrden.purgarFinalizadasAntesDe` + `OrdenJpaRepository.idsFinalizadasAntesDe`
  + su implementación en el doble en memoria, y el evento
  `PuertoObservadorEjecucion.datosAntiguosPurgados` (interfaz + `AdaptadorObservadorLog`
  + catálogo de observabilidad en `src/pruebaCarga/resources/...` si lo referencia).
- Sagas: `ServicioPurgarDatosNegocioHuerfanos`, `CasoUsoPurgarDatosNegocioHuerfanos`,
  `PlanificadorPurgaDatosNegocio`, `RepositorioDatosNegocio.idsHuerfanos` +
  `DatosNegocioJpaRepository.idsHuerfanos`.
- Propiedades `application.yml`/`.properties` de los crons viejos
  (`ordermanager.limpieza.*`, `sagas.purga-datos-negocio.*`).

Aceptación: `./gradlew check` verde, 100 % sin bajar umbral ni añadir exclusiones nuevas,
`ReglasArquitecturaTest` verde. Buscar referencias colgadas (`grep`) antes de cerrar.

---

## Fase 6 — Diagramas y documentación

**Meta:** diagramas y textos al día en el MISMO cambio (regla CLAUDE.md).

- Sustituir el diagrama de secuencia **09** (`09-limpieza-datos`) por dos:
  purga de adjuntos y purga de completadas (con reintento + incidencia). Convención de capas
  (entrada → aplicación → dominio → salida), `activate`/`deactivate`, pasos = llamadas reales
  del código. Convertir a PNG con la skill `puml-to-png` (`-tpng -charset UTF-8`).
- Actualizar **13** (estado operativo: mención a `purgado_en`), **22** (clases aplicación
  soporte: nuevos servicios, fuera los viejos), **24** (infra: nuevos planificadores +
  `AdaptadorIncidenciasLog`, fuera `PlanificadorLimpieza`/`PlanificadorPurgaDatosNegocio`).
- Actualizar el índice `order-manager/docs/README.md` y, si procede, la tabla de la línea 54
  (lista de planificadores) y la sección de purga.
- Revisar `CLAUDE.md`: el ejemplo de purga huérfanos ya no aplica; ajustar si se menciona.

Aceptación: `.puml` y `.png` versionados juntos, README coherente, `./gradlew check` verde.

---

## Cierre (coordinador)

- `./gradlew check` final en verde.
- Commit único o por fase (según prefieras) + push. Borrar este `PLAN-purgas.md`.
- Resumen al usuario: qué se sustituyó, crons nuevos, esquema tocado.
