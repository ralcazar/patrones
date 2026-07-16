# Refactor por fases: SagaRoot + OrdenRoot

Documento de ejecución **fase por fase**. Cada fase está pensada para lanzarse a
un agente independiente. Contiene el diseño ya cerrado (firmas, FSMs, decisiones)
para que ningún agente tenga que re-derivarlo.

- Rama de trabajo: `rediseno-saga-orden-root` (ya creada desde `main`).
- Plan aprobado (contexto y decisiones): `~/.claude/plans/cambia-el-dise-o-para-quirky-riddle.md`.
- Reglas del repo: `CLAUDE.md` (capas `business/**` solo Java + jMolecules;
  diagramas sincronizados; entrada→aplicación→salida).

> **Importante sobre compilación**: las capas están muy acopladas. El árbol
> **NO compila** entre la Fase 1 y el final de la Fase 3. La verificación de
> build es la **Fase 4**. Las fases 1–3 se validan por revisión, no por `gradlew`.
> Si se prefiere validación incremental, lanzar 1→2→3 con el **mismo** agente en
> sesiones encadenadas y compilar solo al cerrar la 3.

---

## 0. Diseño de referencia (leer antes de cualquier fase)

### El agregado (uno solo): OrdenRoot contiene SagaRoot
Hay un ÚNICO agregado por saga. La frontera de consistencia real incluye el
estado de NEGOCIO (FSM) y el de EJECUCIÓN (reintentos/token): varios flujos los
mutan en la misma tx (consumer Kafka, soporte, arranque de secundarias), así que
separarlos en dos agregados violaría "no modificar dos agregados por transacción".
Una sola `version` protege el agregado completo.

- **`OrdenRoot`** (`@AggregateRoot`, `dominio/comun`) — raíz: ejecución + contiene
  la saga: `SagaId sagaId (@Identity)`, `SagaRoot<?> saga` (entidad interna),
  `int intentos`, `Instant proximoReintentoEn`, `UUID tokenTrabajador`,
  `Instant tokenExpiraEn` (lease), `Instant ticketAbiertoEn` (marca operativa de
  ticket, null si no hay), `ResultadoOrden resultado` (null mientras vive),
  `long version` (la ÚNICA del agregado). `tipo()` delega en `saga.tipo()`.
- **`SagaRoot<E extends Enum<E>>`** (abstracto, `@Entity` de jMolecules,
  `dominio/comun`) — SOLO negocio: `SagaId id (@Identity)`, `ExternalId externalId`,
  `E estado` (la FSM), `List<AuditoriaIntervencion> auditoria`. Getters +
  `auditar(...)` protegido. `abstract TipoSaga tipo()`. **Sin `version`** (la
  controla `OrdenRoot`). No conoce reintentos, tiempo ni token.

### FSM de negocio (un enum por saga)
- `EstadoSagaPrincipal`: `INICIAL, PASO1_HECHO, PASO2_HECHO, PASO3_HECHO,
  PASO4_HECHO, PASO5_HECHO, PASO6_HECHO, PASO7_HECHO, TERMINADA,
  COMPENSAR_PASO2, COMPENSAR_PASO1, CANCELADA`.
- `EstadoSagaSecundaria1`: `INICIAL, INICIO_HECHO, TERMINADA`.
- `EstadoSagaSecundaria2`: `INICIAL, ESPERANDO_RESPUESTA, TERMINADA`.
- `EstadoSagaSecundaria3`: `INICIAL, TERMINADA`.
- **Sin** estados operativos (RUNNING/FAILED/SUCCESS): esos son de ejecución.

### `OrdenRoot` — API (todas mutación in-place salvo getters)
**Reloj determinista**: el dominio NUNCA llama a `Instant.now()`. Todo método que
dependa del tiempo recibe `Instant ahora` como último parámetro (lo aporta la capa
de aplicación). Así la escalera de reintentos, el lease y el ticket se testean sin
esperas ni mocks de reloj.

- `static OrdenRoot nueva(SagaRoot<?> saga, Instant ahora)` → intentos 0,
  `proximoReintentoEn=ahora`, token/lease/ticket null, resultado null, version 0.
- `static OrdenRoot rehidratar(saga, intentos, proximoReintentoEn, token, tokenExpiraEn,
  ticketAbiertoEn, resultado, version)`.
- `asignarToken(UUID token, Duration lease, Instant ahora)` → set token, `tokenExpiraEn=ahora+lease`.
- `renovarLease(Duration lease, Instant ahora)` → `tokenExpiraEn=ahora+lease` (en la tx de cada paso completado).
- `boolean tieneTokenVigente(Instant ahora)` → token != null && `tokenExpiraEn` > ahora.
- `liberarToken()` → token=null, tokenExpiraEn=null.
- `resetearIntentos()` → intentos=0 **y `ticketAbiertoEn=null`** (paso OK: la orden
  vuelve a estar "sana"; si se atasca de nuevo más adelante se abrirá un ticket NUEVO).
- `marcarTicketAbierto(Instant ahora)` → `ticketAbiertoEn=ahora`.
- `despertar(Instant ahora)` → `proximoReintentoEn=ahora`, libera token. (eventos externos/soporte)
- `aparcar(Duration d, Instant ahora)` → `proximoReintentoEn=ahora+d`, libera token. (secundaria 2 esperando Kafka)
- `programarReintento(PoliticaReintentos p, Instant ahora)` → `intentos++`;
  `proximoReintentoEn = ahora + p.esperaTras(intentos)`; libera token.
- `finalizar(ResultadoOrden)` → set resultado, libera token.
- `boolean estaViva()` → resultado == null. `SagaRoot<?> saga()`. Getters de todos los campos.

### Lease del token (robustez ante caída de pod)
- El token SIEMPRE se asigna con caducidad (`tokenExpiraEn`). Duración configurable
  (`orden.lease`, p. ej. `PT10M`): holgadamente mayor que el timeout REST más largo
  de UN paso — no necesita cubrir la saga entera porque se renueva con
  `renovarLease` en la tx de cada paso completado.
- Si un pod muere con el token asignado, la orden vuelve a ser candidata del
  planificador al vencer el lease y otro pod la reclama (misma tx con optimistic
  lock: si dos pods ven el mismo token caducado, solo uno gana).
- Si el pod estaba lento pero no muerto (GC, REST colgado), al volver su primer
  `guardar` falla por la `version` única del agregado y se retira: el takeover
  tras lease vencido es seguro a nivel de BD.

### `PoliticaReintentos` (se conserva; añadir un método)
- Escalera intacta: `1, 3, 5, 10, 20, 45, 90, 180` min; del 8º en adelante 180.
- **Añadir** `boolean debeAbrirTicket(int intentos)` → `intentos >= 8` (escalera
  consumida). El planificador de tickets combina esta condición con la marca
  operativa: query `intentos >= 8 AND ticket_abierto_en IS NULL` (ver § Tickets).
- Quitar el uso de `escaleraConsumida` en el flujo (queda o se borra).

### Flujo de ejecución (aplicación)
- **`CasoUsoContinuarSaga.continuar(SagaId, TipoSaga)`** (`ServicioContinuarSaga`):
  1. Reclamar token en tx: cargar OrdenRoot; si `!estaViva()` o `tieneTokenVigente()`
     return; `asignarToken(uuid, lease)`; guardar. Si `ConcurrenciaOptimistaException`
     → return (otro pod).
  2. Bucle:
     ```
     while (true) {
       SenalPaso s;
       try { s = orquestador(tipo).ejecutarPaso(id); }       // REST fuera de tx + UNA tx que guarda el agregado entero
       catch (ConcurrenciaOptimistaException e) { return; }   // otro pod/actor tocó el agregado
       catch (RuntimeException e) {                            // fallo del paso
         tx { orden=cargar; orden.programarReintento(politica); guardar; } return;
       }
       switch (s) {
         case HayMasTrabajo       -> continue;  // el orquestador ya reseteó intentos y renovó el lease
         case Aparcar, Finalizada -> return;    // ya persistido por el orquestador
       }
     }
     ```
  - `sealed interface SenalPaso { record HayMasTrabajo(); record Aparcar(Duration ventana);
    record Finalizada(ResultadoOrden resultado); }` (en `aplicacion`).
- **`OrquestadorSaga`** (interfaz aplicación): `TipoSaga tipo();
  SenalPaso ejecutarPaso(SagaId id);`. Cuatro implementaciones (una por saga).
  `ejecutarPaso`: carga la OrdenRoot (lectura), toma `saga().comandoActual()`, hace
  el REST del paso FUERA de tx (puede lanzar → reintento), y en UNA sola tx recarga
  el agregado, aplica en la saga (`aplicarYAvanzar`, etc.) Y la parte operativa de
  la señal (`resetearIntentos()+renovarLease(lease)` | `aparcar(d)` | `finalizar(r)`),
  y guarda UNA vez (una sola `version` para negocio + ejecución). Devuelve la señal.
  Los orquestadores reciben `RepositorioOrden`, `UnidadDeTrabajo` y la duración del
  lease. `ServicioContinuarSaga` tiene `Map<TipoSaga,OrquestadorSaga>`.

### Planificador (adaptador de entrada, uno por pod)
- `PlanificadorContinuacion` `@Scheduled(fixedDelay ...)`: `RepositorioOrden.buscarEjecutables(now, limite)`
  devuelve filas `(sagaId, tipo)` con `proximoReintentoEn<=now AND resultado IS NULL
  AND (tokenTrabajador IS NULL OR tokenExpiraEn<=now)` — el segundo OR reclama
  tokens de pods muertos al vencer el lease. Para cada una invoca
  `casoUsoContinuarSaga.continuar(sagaId, tipo)`.
  (El reclamo de token con optimistic lock lo hace el caso de uso.)

### Secundaria 2 (evento async)
- `INICIAL`: el orquestador hace `puertoSagaSecundaria2.solicitar(...)`, luego
  `saga.solicitudEnviada()` (estado→`ESPERANDO_RESPUESTA`); devuelve `Aparcar(3h)`.
- `ESPERANDO_RESPUESTA` (vence el aparcado o timeout): el orquestador hace
  `conciliacion.consultar(...)`: `Disponible(ref)`→`saga.respuestaRecibida(ref)`
  (→TERMINADA)→`Finalizada(FINALIZADA_OK)`; `SinResultado`→`Aparcar(3h)`;
  `FalloRegistrado(m)`→lanzar excepción (→reintento con backoff, reconcilia).
- Consumer Kafka despierta: `CasoUsoRegistrarRespuestaSecundaria2.respuestaOk`:
  dedup por mensajeId; tx { orden=cargar; `orden.saga().respuestaRecibida(ref)`;
  `orden.despertar()`; guardar (UN solo save del agregado) }.
  `respuestaError`: tx { orden=cargar; `orden.saga().volverASolicitar()` (estado→INICIAL);
  `orden.programarReintento(politica)`; guardar }.
- **Ventana = 3h** (`Duration.ofHours(3)`), antes 24h.

### Tickets (derivados de intentos + marca operativa)
- Se elimina `EstadoTicket` y el marcador de NEGOCIO en la saga. La marca pasa a
  ser OPERATIVA: `OrdenRoot.ticketAbiertoEn` (null = sin ticket abierto).
- `PuertoSagasTicketPendiente.buscar()` → query sobre `orden`:
  `intentos >= 8 AND ticket_abierto_en IS NULL AND resultado IS NULL`.
  (Robusto: no hay ventana de 180 min como con `intentos = 8` exacto — si el
  planificador de tickets estuvo caído, la condición sigue visible al volver.)
- `ServicioTicketsSoporte`: por cada pendiente, `tickets.abrir(...)` y después
  tx { cargar orden; `marcarTicketAbierto(ahora)`; guardar } (si conflicto
  optimista, recargar y reintentar solo el marcado). Semántica at-least-once:
  a lo sumo un ticket duplicado si el pod muere entre abrir y marcar.
- Ciclo de vida de la marca: un paso OK (`resetearIntentos()`) la pone a null,
  igual que pone `intentos=0` → un atasco posterior (otros 8 intentos) abre un
  ticket nuevo; mientras el atasco actual dure, no se duplica.

### Compensación (solo principal)
- `cancelar(quien, motivo)`: si `TERMINADA`→`SagaYaCompletadaException`; si
  `COMPENSAR_*`/`CANCELADA`→ no-op idempotente; si `PASO7_HECHO`→`PuntoNoRetornoSuperadoException`;
  si no: `>=PASO2_HECHO`→`COMPENSAR_PASO2`; `>=PASO1_HECHO`→`COMPENSAR_PASO1`; en otro
  caso→`CANCELADA`. Auditar.
- Orquestador principal: `COMPENSAR_PASO2`→`puertoPaso2.compensar`→`compensacionCompletada()`
  (→`COMPENSAR_PASO1`)→`HayMasTrabajo`; `COMPENSAR_PASO1`→`puertoPaso1.compensar`→
  `compensacionCompletada()` (→`CANCELADA`)→`Finalizada(FINALIZADA_COMPENSADA)`;
  `CANCELADA`→`Finalizada(FINALIZADA_COMPENSADA)`.
- Fallo de compensación = excepción → reintento (se elimina el estado BLOQUEADO_SOPORTE).

### Arranque de secundarias (principal, al llegar a TERMINADA tras PASO8)
- En la MISMA tx que avanza la principal a `TERMINADA`, el orquestador principal
  crea 3 agregados hijos: `RepositorioOrden.crear(OrdenRoot.nueva(sagaHija))` × 3
  (cada OrdenRoot lleva su SagaRoot hija dentro; una llamada persiste saga+orden).
  Devuelve `Finalizada(FINALIZADA_OK)`. Sustituye a `Decision.ArrancarSaga`.
  (CREAR agregados nuevos en la tx es la excepción aceptada a la regla DDD;
  solo se MODIFICA un agregado: el de la principal.)

### Datos manuales de soporte (`marcarPasoOk`)
- Cada SagaRoot expone `marcarPasoActualOkManual(UsuarioSoporte, String justificacion,
  Map<String,String> datos)`: construye internamente el `ResultadoPaso` del paso del
  estado actual (con la lógica que hoy vive en `FabricaResultadoManual`), lo aplica,
  avanza la FSM y audita. Se elimina `FabricaResultadoManual` (su mapeo se inlinea en
  las sagas). Si faltan datos obligatorios → `DatosManualesRequeridosException`.

### Read model de soporte (desacoplar de la FSM)
- Los estados en los DTOs de `CasoUsoConsultarSagasSoporte` pasan a **String**
  (vienen de queries SQL; CQRS). `SagaResumen`/`PasoDetalle`/`FiltroSagas` dejan de
  importar `EstadoSaga`/`EstadoPaso`/`PasoSaga`/`EstadoTicket`. `sagasConTicket(EstadoTicket)`
  → `sagasConTicketPendiente()` (query de tickets pendientes de §0). `sagasEnEjecucion()`
  = `tokenTrabajador IS NOT NULL AND tokenExpiraEn > now AND resultado IS NULL`
  (un token caducado ya no cuenta como "en ejecución"). `sagasBloqueadas()` =
  `intentos >= 8`.

### Reglas transversales
- **Idioma español** en todo símbolo nuevo (ver `~/.claude/.../memory/nomenclatura-espanol.md`).
- `business/**` solo Java + jMolecules; JPA/`@Version`/Spring solo en `infraestructure/**`.
- **La base de datos es ORACLE**: migraciones Flyway en dialecto Oracle
  (`VARCHAR2`, `TIMESTAMP(6)`, `NUMBER`; sin `SERIAL`/`IDENTITY` — la PK es
  `saga_id`; paginación con `FETCH FIRST :limite ROWS ONLY`; Oracle no tiene
  índices parciales → índice funcional
  `CASE WHEN resultado IS NULL THEN proximo_reintento_en END`).
  Dependencias: `runtimeOnly 'com.oracle.database.jdbc:ojdbc11'` +
  `implementation 'org.flywaydb:flyway-database-oracle'` (fuera `postgresql` y
  `flyway-database-postgresql`). Datasource `jdbc:oracle:thin:@...`.
- (Resuelto) El antiguo caveat de la orden bloqueada por pod muerto queda cubierto
  por el lease de token (§ "Lease del token"): ya no aplica.

---

## Inventario global

### Borrar (dominio)
`Saga.java`, `EstadoSaga.java`, `EstadoTicket.java`, `EstadoPaso.java`,
`EjecucionPaso.java`, `Decision.java`, `PasoSaga.java`,
`sagaprincipal/PasoSagaPrincipal.java`, `sagasecundaria1/PasoSagaSecundaria1.java`,
`sagasecundaria2/PasoSagaSecundaria2.java`, `sagasecundaria3/PasoSagaSecundaria3.java`.

### Borrar (aplicación)
`servicio/ServicioSagaBase.java`, `servicio/ServicioDespachoTareas.java`,
`servicio/ManejadorTareasSaga.java`, `servicio/FabricaResultadoManual.java`,
`servicio/ReintentoOptimista.java` (o reducir), `tarea/TareaSaga.java`,
`tarea/TareaReclamada.java`, `puerto/entrada/CasoUsoDespacharTareas.java`,
`puerto/entrada/CasoUsoProcesarResultadoPaso.java`,
`puerto/salida/PuertoColaTareas.java`, `puerto/salida/PuertoRecepcionTareas.java`,
`puerto/salida/RepositorioSagaPrincipal.java`,
`puerto/salida/RepositorioSagaSecundaria1.java`,
`puerto/salida/RepositorioSagaSecundaria2.java`,
`puerto/salida/RepositorioSagaSecundaria3.java` (la escritura del agregado
completo pasa por el único `RepositorioOrden`).

### Borrar (infraestructura)
Todo `infraestructure/ordermanager/cola/**` (8 ficheros),
`saga/AdaptadorColaTareas.java`, `saga/AdaptadorRecepcionTareas.java`,
`saga/CodecTareaSaga.java`; y el test `test/.../saga/CodecTareaSagaTest.java`.

### Crear (dominio)
`ResultadoOrden.java` ✅ (ya creado), `OrdenRoot.java`, `SagaRoot.java`,
`EstadoSagaPrincipal.java`, `EstadoSagaSecundaria1.java`, `EstadoSagaSecundaria2.java`,
`EstadoSagaSecundaria3.java`.

### Crear (aplicación)
`puerto/entrada/CasoUsoContinuarSaga.java`, `puerto/salida/RepositorioOrden.java`,
`servicio/ServicioContinuarSaga.java`, `servicio/SenalPaso.java`,
`servicio/OrquestadorSaga.java`.

### Crear (infraestructura)
Entidades JPA `SagaEntity` (SIN `@Version`) + `OrdenEntity` (con `@Version`, la
única del agregado) y UN adaptador `RepositorioOrden` que persiste/carga el
agregado completo (despacha la subclase de `SagaRoot` por `tipo`) e implementa
la query del planificador; `PlanificadorContinuacion`; migraciones Flyway
`saga`/`orden` en dialecto **Oracle**.

---

## Fase 1 — Dominio: SagaRoot + OrdenRoot + FSMs

**Objetivo**: dejar el paquete `dominio/**` en el modelo nuevo. (No compila el módulo
todavía; se valida por revisión de que el dominio es coherente y autónomo.)

**Crear**
- `dominio/comun/ResultadoOrden.java` ✅ (hecho).
- `dominio/comun/OrdenRoot.java` — API de §0. `@AggregateRoot`, `@Identity sagaId`,
  contiene `SagaRoot<?> saga`; lease (`tokenExpiraEn`) y marca de ticket
  (`ticketAbiertoEn`); única `version`; reloj determinista (`Instant ahora` como
  parámetro, nunca `Instant.now()`).
- `dominio/comun/SagaRoot.java` — base abstracta de §0: entidad interna del
  agregado (`@Entity` de jMolecules), SIN `version`.
- Los 4 enums `EstadoSaga*` de §0 (en el paquete de cada saga).

**Editar**
- `dominio/comun/PoliticaReintentos.java` — añadir `debeAbrirTicket(int)`.
- `dominio/comun/PasoNoIntervenibleException.java` y `DatosManualesRequeridosException.java`
  — dejar de referenciar `PasoSaga`/`EstadoPaso`; firma `(SagaId, String detalle)`.
- Los 4 `*Root.java` — reescribir extendiendo `SagaRoot<EstadoSaga*>`:
  - Métodos: `comandoActual()` (switch sobre estado → ComandoPaso*), `aplicarYAvanzar(ResultadoPaso)`
    (aplica refs/ctx y avanza un estado), `terminada()`, `resultadoFinal()` (FINALIZADA_OK;
    principal: COMPENSADA si CANCELADA), `marcarPasoActualOkManual(...)`.
  - Principal además: `cancelar(quien,motivo)`, `compensacionCompletada()`, `contextosArranque()`,
    `esCancelable()`. `crear(...)`→estado `INICIAL`. Conserva `ContextoTramitacion ctx`.
  - Secundaria2 además: `solicitudEnviada()`, `respuestaRecibida(RefRespuesta)`, `volverASolicitar()`.
  - Conservar `rehidratar(...)` en cada uno (sin `EstadoTicket`/`ticketAbiertoEn`/`pasos` map;
    ahora con `E estado`).

**Borrar**: los 11 ficheros de la sección "Borrar (dominio)".

**Criterio de hecho**: `grep -r "EstadoSaga\b\|EstadoTicket\|EstadoPaso\|EjecucionPaso\|Decision\|PasoSaga\|class Saga<" dominio/` no encuentra referencias vivas dentro de `dominio/`; cada `*Root` compila conceptualmente contra `SagaRoot` y sus Comando/Resultado.

---

## Fase 2 — Aplicación: continueSaga + reescritura de casos de uso

**Objetivo**: capa `aplicacion/**` en el modelo nuevo. Depende de Fase 1.

**Crear**
- `puerto/entrada/CasoUsoContinuarSaga.java` — `void continuar(SagaId, TipoSaga)`.
- `puerto/salida/RepositorioOrden.java` — ÚNICO puerto de persistencia de escritura:
  `crear(OrdenRoot)` (persiste el agregado completo: orden + saga + auditoría),
  `cargar(SagaId)` (rehidrata el agregado completo), `guardar(OrdenRoot)` (lanza
  `ConcurrenciaOptimistaException` sobre la única `version`),
  `List<CandidataOrden> buscarEjecutables(Instant ahora, int limite)`
  (`record CandidataOrden(SagaId sagaId, TipoSaga tipo)`; query con lease de §0),
  `long purgarFinalizadasAntesDe(Instant corte)` (borra el agregado completo).
- `servicio/SenalPaso.java` (sealed §0), `servicio/OrquestadorSaga.java` (interfaz §0),
  `servicio/ServicioContinuarSaga.java` (bucle §0, `Map<TipoSaga,OrquestadorSaga>`,
  `UnidadDeTrabajo`, `RepositorioOrden`, `PoliticaReintentos`, duración del lease).

**Editar (reescribir)**
- `ServicioEncolarTramitacion` → **renombrar** `ServicioIniciarTramitacion`:
  tx { `RepositorioOrden.crear(OrdenRoot.nueva(new SagaPrincipalRoot(INICIAL)))` }
  (una sola llamada persiste saga + orden).
- Los 4 orquestadores `ServicioSaga*` → implementar `OrquestadorSaga` (ya no extienden
  `ServicioSagaBase`, ya no emiten `Decision`). Cargan/recargan el agregado por
  `RepositorioOrden` y guardan UNA vez por paso (negocio + operativo, §0).
  Principal: crear los 3 agregados hijos al TERMINAR.
  Secundaria2: solicitar/aparcar/conciliar + `solicitudEnviada/respuestaRecibida/volverASolicitar`.
- `ServicioRegistrarRespuestaSecundaria2` → una tx, una carga, un `guardar` (§0), dedup.
- `ServicioSoporteSagas` → `reintentarPaso`: tx { orden.resetearIntentos()+despertar(); guardar };
  `marcarPasoOk`: tx { `orden.saga().marcarPasoActualOkManual(...)`; `orden.despertar()`; guardar };
  `cancelarPrincipal`: tx { `orden.saga().cancelar(...)`; `orden.despertar()`; guardar }.
  Consultas → nuevas firmas. (Siempre UN agregado, UN save.)
- `ServicioTicketsSoporte` → `pendientes.buscar()` + por cada una `tickets.abrir(...)`
  y tx { cargar orden; `marcarTicketAbierto(ahora)`; guardar } (§0-Tickets).
- `ServicioLimpiezaDatos` → `RepositorioOrden.purgarFinalizadasAntesDe` (agregado
  completo) + dedup de mensajes.
- Interfaces de entrada: `CasoUsoConsultarSagasSoporte` (DTOs a String, `sagasConTicketPendiente()`),
  `CasoUsoRegistrarRespuestaSecundaria2` (doc), `CasoUsoIntervenirSaga` (doc: nombrePaso opcional).
- Puertos de salida: `PuertoConsultaSagasSoporte`, `PuertoSagasTicketPendiente` (§0),
  `PuertoTicketsSoporte` (igual). Los 4 `RepositorioSaga*` se BORRAN (ver inventario).

**Borrar**: los ficheros de "Borrar (aplicación)".

**Criterio de hecho**: sin referencias a `TareaSaga`/`PuertoColaTareas`/`Decision`/`ServicioSagaBase`
en `aplicacion/**`; el bucle `ServicioContinuarSaga` cubre reintento/aparcar/finalizar.

---

## Fase 3 — Infraestructura: JPA + planificador + migraciones + config

**Objetivo**: adaptadores nuevos; el módulo vuelve a estar cableado. Depende de Fase 2.

**Crear**
- JPA: `SagaEntity` (SIN `@Version`) + `OrdenEntity` (`@Version`, la única del
  agregado) y UN adaptador de `RepositorioOrden` que mapea el agregado completo
  dominio↔entidades (despacha la subclase de `SagaRoot` por `tipo`; `guardar`
  traduce `OptimisticLockException` → `ConcurrenciaOptimistaException`) e
  implementa `buscarEjecutables` en SQL **Oracle**:
  `proximo_reintento_en<=:ahora AND resultado IS NULL AND (token_trabajador IS NULL
  OR token_expira_en<=:ahora) ORDER BY proximo_reintento_en FETCH FIRST :limite ROWS ONLY`.
- `saga/PlanificadorContinuacion.java` — `@Scheduled` → `buscarEjecutables` → `casoUso.continuar`.
- Adaptador del read model de soporte y del `PuertoSagasTicketPendiente`
  (query `intentos >= 8 AND ticket_abierto_en IS NULL AND resultado IS NULL`).
- Migraciones Flyway en dialecto **Oracle** (borrar los `.sql.txt` actuales, que
  eran PostgreSQL): `V1__crear_saga.sql` (tabla `saga`: `saga_id VARCHAR2(36) PK`,
  `tipo`, `external_id`, `estado VARCHAR2(40)`, contexto; tabla hija de auditoría)
  y `V2__crear_orden.sql` (tabla `orden`: `saga_id VARCHAR2(36) PK` + FK a `saga`,
  `intentos NUMBER(10)`, `proximo_reintento_en TIMESTAMP(6)`,
  `token_trabajador VARCHAR2(36)`, `token_expira_en TIMESTAMP(6)`,
  `ticket_abierto_en TIMESTAMP(6)`, `resultado VARCHAR2(30)`,
  `version NUMBER(19)`; índice funcional de candidatas
  `CASE WHEN resultado IS NULL THEN proximo_reintento_en END` — Oracle no tiene
  índices parciales — e índice por `intentos` para tickets/bloqueadas).
- `build.gradle`: `runtimeOnly 'com.oracle.database.jdbc:ojdbc11'` +
  `implementation 'org.flywaydb:flyway-database-oracle'`; quitar `postgresql`
  y `flyway-database-postgresql`.

**Editar / Borrar**
- `ConsumidorRespuestaSecundaria2` → invoca el registrar nuevo (sin cambios de firma del
  caso de uso).
- `ConfiguracionAplicacion` → nuevos beans, fuera los de cola.
- `ConfiguracionEjecucionAsincrona` → revisar (el planificador puede necesitar pool simple).
- **Borrar** `cola/**` + `AdaptadorColaTareas` + `AdaptadorRecepcionTareas` + `CodecTareaSaga`
  + `test/.../CodecTareaSagaTest.java`.
- Ajustar `application.yml`: datasource **Oracle** (`jdbc:oracle:thin:@...`);
  bloque `orden:` con `lease: PT10M`, intervalo del planificador y `limite` de
  candidatas; fuera el bloque `gestor-ordenes` de la cola antigua.

**Criterio de hecho**: existe un adaptador por cada puerto que la aplicación necesita; no
quedan referencias a `Orden`/`ordenes`/cola.

---

## Fase 4 — Build + ArchUnit + verificación

**Objetivo**: `./gradlew :order-manager:build` verde.

- Compilar; resolver errores residuales de las fases 1–3.
- `ReglasArquitecturaTest` en verde (business puro). Añadir, si procede, una regla que
  verifique que `OrdenRoot`/`SagaRoot` están en dominio y que JPA solo vive en infra.
- Ejercitar los flujos de verificación del plan aprobado (flujo feliz, reintento con
  escalera `1,3,5,…`, concurrencia con OptimisticLock ignorado, secundaria2 3h + despertar,
  ticket al consumir la escalera (`intentos>=8` sin marca; sin duplicados mientras
  dura el atasco; ticket nuevo si se recupera y vuelve a atascarse),
  compensación → `FINALIZADA_COMPENSADA`) y los nuevos:
  **lease vencido** (pod muerto → la orden reaparece como candidata y otro pod la
  reclama) y **takeover seguro** (el pod lento que vuelve falla por `version` al
  guardar y se retira sin corromper).
- Tests con BD: los `.sql` son Oracle; si algún test levanta JPA/Flyway, usar H2 en
  `MODE=Oracle` (y si el índice funcional no es compatible, aislarlo en una migración
  solo-Oracle) o Testcontainers con `gvenzl/oracle-free`.

**Criterio de hecho**: build y tests verdes.

---

## Fase 5 — Diagramas (.puml → .png) + README

**Objetivo**: diagramas sincronizados (obligatorio por `CLAUDE.md`).

- Editar en `order-manager/docs/`: **14** (agregado ÚNICO OrdenRoot⊃SagaRoot, una
  `version`), **13** (FSM `EstadoSaga*` + `ResultadoOrden`/operativo; fuera
  EstadoTicket/EstadoPaso), **17** (continueSaga + planificador con **lease**:
  reclamo, renovación por paso, reclamo de token caducado; fuera TareaSaga/despacho),
  **15/16** (agregados por saga); secuencias **01, 02, 03, 05 (3h), 07 (ticket
  `intentos>=8` sin marca + `marcarTicketAbierto`), 08**; reescribir **23**
  (planificador + tablas `saga`/`orden` Oracle + query de candidatas con lease)
  y **24**.
- Regenerar PNG con la skill `puml-to-png` (`-tpng -charset UTF-8`, postBuffer amplio).
- Actualizar `order-manager/docs/README.md`.

**Criterio de hecho**: cada `.puml` tocado tiene su `.png` regenerado; README al día.

---

## Fase 6 — Cierre

- `git add -A && git commit` en `rediseno-saga-orden-root` (mensaje describiendo el
  rediseño; co-author y sesión según convención del repo).
- `git push -u origin rediseno-saga-orden-root` y abrir PR si el usuario lo pide.
- (Ver memoria `flujo-commits-refactor`.)
