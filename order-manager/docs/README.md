# Diagramas de order-manager

Diagramas PlantUML del motor de órdenes (`business.ordermanager` /
`infraestructure.ordermanager`) y de las sagas concretas construidas sobre él
(`business.sagas` / `infraestructure.sagas`). Los `.puml` y sus `.png` se
versionan juntos y se actualizan en el mismo cambio que el código que
documentan (ver `CLAUDE.md` en la raíz del repo).

Modelo: un ÚNICO agregado por orden, `OrdenRoot` (ejecución: intentos, lease
del token, ticket, completadaEn, y sus propias marcas temporales `creadaEn`
—inmutable, fijada al crear— y `actualizadaEn` —el propio agregado la fija en
cada mutación con el `ahora` de la operación, ya no un `@PrePersist`/
`@PreUpdate` de infraestructura, ver 14 y 23) que contiene su `Proceso<E>`
(entidad interna, negocio: FSM `EstadoSaga*` + auditoría). `Proceso<E>` y `OrdenRoot` son
vocabulario neutro del motor — "saga" solo existe del lado de
`business.sagas` (sus 4 subclases concretas: `SagaPrincipal`,
`SagaSecundaria1/2/3`). No hay cola de tareas: un planificador por pod
(`PlanificadorContinuacion`) comprueba si hay trabajo (EXISTS barato) y
despierta hasta N workers pull (`TrabajadorContinuacion`, `@Async`); cada
worker reclama candidatas por lease (`continuarSiguiente`, una por pull) y la
capa de aplicación (`ServicioContinuarOrden` + un `ProcesadorOrden` por tipo
de orden) las avanza paso a paso.

## La frontera ordermanager ↛ sagas

El motor (`business.ordermanager` + `infraestructure.ordermanager`) es
genérico en el tipo de orden: no importa ninguna clase de `business.sagas` ni
de `infraestructure.sagas`, y ninguna de sus clases puede llamarse `*Saga*`
(vocabulario neutro). La dependencia va siempre `sagas -> ordermanager`,
nunca al revés; lo impone `ReglasArquitecturaTest` (regla
`ordermanagerNoDependeDeSagas`, ArchUnit, sobre producción y tests). El motor
expone 3 puntos de extensión (SPI) para que una aplicación nueva pueda
definir sus propios tipos de orden sin tocar `ordermanager`:

| SPI | Paquete | Qué resuelve |
|---|---|---|
| `ProcesadorOrden` | `business.ordermanager.aplicacion.servicio` | Ejecuta un paso de un tipo de orden (ver 17) |
| `MapeadorProceso` | `infraestructure.ordermanager.persistencia` | Persiste/rearma el contexto de un `Proceso<?>` por tipo en SU tabla satélite (ver 23) |
| `DescriptorSoporteOrden` | `infraestructure.ordermanager.persistencia` | Paso pendiente / cancelable / datos manuales por tipo, para la pantalla de soporte (ver 23) |

Las sagas implementan las 3 en `business.sagas`/`infraestructure.sagas`
(`ServicioSagaPrincipal`/`Secundaria1/2/3` y `SoporteSagaPrincipal`/
`Secundaria1/2/3`, ver 18-21 y 24) y se registran ante el motor como
`List<ProcesadorOrden>` / `List<MapeadorProceso>` / `List<DescriptorSoporteOrden>`
inyectados por Spring — el motor los indexa por `tipo()` sin conocer sus
clases concretas.

Guía paso a paso para añadir un tipo de orden nuevo (una saga nueva u otro
proceso de negocio) sobre estas 3 SPI: [extender-saga.md](extender-saga.md).

## Convención de los diagramas de secuencia

Cada diagrama separa las capas en bloques (`box`), de izquierda a derecha:

| Bloque | Color | Contenido |
|---|---|---|
| Adaptadores de entrada | azul `#EFF5FB` | `PlanificadorContinuacion`/`PlanificadorTicketsSoporte`/`PlanificadorPurgaAdjuntos`/`PlanificadorPurgaCompletadas` (`@Scheduled`), `TrabajadorContinuacion` (worker pull `@Async`), consumer Kafka, `ControladorTramitaciones` (REST `POST /tramitaciones`) |
| Aplicación | verde `#F5FBEF` | casos de uso, `ServicioContinuarOrden` y los `ProcesadorOrden` de cada saga (`ServicioSagaPrincipal`/`Secundaria1/2/3`) |
| Dominio | naranja `#FBF5EF` | el agregado (`OrdenRoot` ⊃ `Proceso`: `SagaPrincipal`/`SagaSecundariaN`) |
| Adaptadores de salida | violeta `#F3EFFB` | `AdaptadorRepositorioOrden` (persistencia del agregado), puertos REST/Kafka de cada paso |

Regla de oro en todos los flujos: **dentro de la transacción solo BBDD**
(el agregado `OrdenRoot` completo: negocio + ejecución, un único `guardar`);
**fuera de ella solo I/O externo** (REST del paso, tickets). Y en el bucle de
`ServicioContinuarOrden`, **sin cargas redundantes**: el primer paso reutiliza
la MISMA instancia que `reclamarToken` ya cargó, mutó (token) y guardó —
como `guardar` incrementa la `version` en exactamente 1 (JPA `@Version`),
`reclamarToken` la reconstruye con `version + 1` sin una 2ª carga a BBDD y la
devuelve (`Optional<OrdenRoot>`) lista para el `ProcesadorOrden`
(`ejecutarPaso(orden)`); a partir del segundo paso (señal `HayMasTrabajo`) sí
hace falta una carga real, porque el procesador ya guardó esa instancia y su
`version` en memoria queda obsoleta. En todos los casos, tanto la transacción
que cierra el paso como, si el REST falla, la que programa el reintento
guardan esa MISMA instancia (con su `version`), de modo que si otro actor
escribió entre medias el `guardar` falla por `version` y el pod se retira
(takeover seguro, sin recargas que anulen el optimistic locking).

La frontera transaccional es `@Transactional` (`jakarta.transaction`)
directamente en los métodos de los servicios de aplicación: no hay puerto
`UnidadDeTrabajo`. Los servicios con REST fuera de tx (los `ServicioSaga*` de
`business.sagas`, `ServicioContinuarOrden`, `ServicioTicketsSoporte`) se
inyectan a sí mismos el proxy transaccional de Spring (`self`, cableado con
un parámetro `@Lazy` en `ConfiguracionOrderManager` o `ConfiguracionSagas`
según a qué módulo pertenezcan) e invocan su parte `@Transactional` a través
de él (`self.aplicarX(...)`), porque una auto-invocación normal saltaría el
proxy. Los servicios donde toda la operación pública es una única
transacción sin REST intercalado (`ServicioSoporteOrdenes`,
`ServicioCancelarTramitacion`, `ServicioRegistrarRespuestaSecundaria2`)
anotan el método público directamente, sin necesidad de `self`.
`ServicioIniciarTramitacion` sí lo necesita aunque no sea un
`ProcesadorOrden`: pide los datos de negocio por REST antes de crear los
agregados, así que su método público también intercala I/O externo con la
parte `@Transactional`. Las dos purgas por tramitación
(`ServicioPurgarAdjuntos`/`ServicioPurgarCompletadas`, ver 09-10 y 22)
también usan `self` pese a no tener REST intercalado: su método público no
es `@Transactional` (el reintento operativo necesita repetir la ejecución
completa intento a intento), así que invoca `self.ejecutar(corte)` para que
esa parte sí pase por el proxy.
Las flechas fluyen de izquierda a derecha y se muestran las líneas de
activación.

## Diagramas de secuencia

| Diagrama | Qué muestra |
|---|---|
| [01-arranque-saga-nueva](01-arranque-saga-nueva.png) | `POST /tramitaciones` (`ControladorTramitaciones`): idempotente vía `PuertoBusquedaTramitacion`; si no existe, `PuertoDatosNegocio.obtener` fuera de tx y `ServicioIniciarTramitacion.crearAgregados` (`@Transactional`) crea `DatosNegocio` + `SagaPrincipal` + `OrdenRoot` con la prioridad derivada de `datoNegocio3().prioridad()` (con el camino 502 si el servicio externo falla y el de carrera si el índice único de `datos_negocio.external_id` lo rechaza); `PlanificadorContinuacion` descubre el trabajo (`hayTrabajoPendiente`) y despierta a los workers pull, que la reclaman con `continuarSiguiente` (`buscarEjecutables`, orden `prioridad DESC, creada_en, proximo_reintento_en`) |
| [02-pasos-saga-principal](02-pasos-saga-principal.png) | Bucle de `ServicioContinuarOrden` + `ServicioSagaPrincipal.ejecutarPaso` (`ProcesadorOrden`): reclamo de token, REST fuera de tx y checkpoint transaccional (`resetearIntentos`+`renovarLease`) por cada uno de PASO1..PASO8 |
| [03-finalizacion-saga-principal](03-finalizacion-saga-principal.png) | Al completar PASO8: `orden.finalizar(ahora)` (`completadaEn`) + creación de las 3 sagas hijas (`RepositorioOrden.crear` ×3), HEREDANDO cada una la prioridad de la principal (`orden.prioridad()`, sin releer `datos_negocio`), en la misma tx (sin eventos) |
| [04-saga-secundaria1](04-saga-secundaria1.png) | Saga secundaria 1: INICIO → CONFIRMACION, dos llamadas REST a métodos distintos del mismo servicio |
| [05-saga-secundaria2](05-saga-secundaria2.png) | Saga secundaria 2: solicitud REST, respuesta diferida por evento Kafka (siempre éxito, sin caso de error en el contrato; puede tardar), aparcado de 3 h y conciliación REST si vence (nueva ventana de 3 h) |
| [06-saga-secundaria3](06-saga-secundaria3.png) | Saga secundaria 3: una única llamada REST y `orden.finalizar(ahora)` directo |
| [07-error-ticket-soporte](07-error-ticket-soporte.png) | Fallo de un paso: `programarReintento` con backoff 1..180 min indefinido guardando `DetalleError` (clase+mensaje de la excepción, sin stacktrace) en `OrdenRoot.ultimoError`, y el barrido `@Scheduled` que abre UN ticket cuando `intentos>=8 AND ticketAbiertoEn IS NULL`, incluyendo ese detalle en el log del ticket |
| [08-operaciones-soporte](08-operaciones-soporte.png) | `ServicioSoporteOrdenes` (motor): consultas (CQRS), reintentar/marcarPasoOk; `ServicioCancelarTramitacion` (sagas): cancelar con compensación asíncrona (la ejecuta el mismo bucle de continuación, no el request de cancelación) |
| [09-purga-adjuntos](09-purga-adjuntos.png) | `PlanificadorPurgaAdjuntos` (23:00, retención 30 días) → `ServicioPurgarAdjuntos`: criterio POR TRAMITACIÓN (`RepositorioOrden.externalIdsFinalizadosAntesDe`) + `RepositorioDatosNegocio.idsPorExternalIdsSinPurgar`, luego por cada id `cargar` + `DatosNegocio.purgar(ahora)` (el dominio sella `purgadoEn`) + `purgarAdjuntos(id, purgadoEn)` (anula el contenido de los documentos, sin borrar filas, y transporta ese valor ya sellado a la columna `purgado_en`); reintento operativo (`ReintentoOperativo`, hasta 5 intentos vía `self`) y, si se agotan, `PuertoIncidencias.abrir` |
| [10-purga-completadas](10-purga-completadas.png) | `PlanificadorPurgaCompletadas` (23:30, retención 180 días) → `ServicioPurgarCompletadas`: mismo criterio por tramitación, pero BORRA `RepositorioOrden.purgarPorExternalIds` (las 4 órdenes, sin `ON DELETE CASCADE`: hijas→padre) y `RepositorioDatosNegocio.borrar` de cada `datos_negocio` — las órdenes se borran antes por la FK de `proceso_saga_principal` a `datos_negocio`; mismo reintento operativo + incidencia que 09 |

## Diagramas de estado y de clases

Los diagramas de clases están troceados por subconjunto (capa + saga) para
que se lean sin cruces de líneas: uno por capa de dominio/aplicación de cada
saga, más el núcleo del motor, el shared kernel de sagas, soporte e
infraestructura.

| Diagrama | Qué muestra |
|---|---|
| [13-maquinas-de-estado](13-maquinas-de-estado.png) | Las 4 FSM de negocio `EstadoSagaPrincipal`/`Secundaria1`/`Secundaria2`/`Secundaria3` + el estado operativo de finalización `completadaEn` y el sentinela operativo `purgado_en` de `DatosNegocio` (ajeno a `OrdenRoot`); nota de que intentos/lease/ticket son atributos operativos de `OrdenRoot`, no una FSM (ya no hay `EstadoTicket` ni `EstadoPaso`) |
| [14-clases-dominio-ordermanager](14-clases-dominio-ordermanager.png) | Dominio del motor genérico (`business.ordermanager.dominio`): el agregado único `OrdenRoot` ⊃ `Proceso<E>` (`@ValueObject` inmutable interno: cada transición devuelve una instancia nueva, `OrdenRoot.reemplazarProceso(nuevo, ahora)` la sustituye; una sola `version` la controla el agregado), `creadaEn`/`actualizadaEn` MODELADAS por el propio agregado (`creadaEn` inmutable, fijada al crear con el `ahora` de la factoría; `actualizadaEn` la fija cada mutador — `resetearIntentos(ahora)`, `reemplazarProceso(nuevo, ahora)`, etc. — con el `ahora` de su operación, reloj determinista, ya no un `@PrePersist`/`@PreUpdate` de infraestructura, ver 23), `Prioridad` (VO neutro, `peso: int`, `normal()` = 0: metadato de planificación que el motor ordena sin saber quién lo determina — ver 26), `TipoOrden` (VO abierto), `PoliticaReintentos`, `DetalleError` (clase+mensaje del último fallo, para soporte), excepciones y VOs del motor — sin ninguna clase de sagas |
| [15-clases-dominio-saga-principal](15-clases-dominio-saga-principal.png) | Dominio de la saga principal (`business.sagas.dominio.sagaprincipal`): `SagaPrincipal` (`@ValueObject` inmutable, extiende `Proceso<EstadoSagaPrincipal>`; sus transiciones — `aplicarYAvanzar`, `cancelar`, `compensacionCompletada`, `marcarPasoActualOkManual` — devuelven instancia nueva), su constante `TIPO`, comandos/resultados por paso, `ContextoTramitacion` (referencia `DatosNegocioId` — ver 26 — en vez de contener datos de negocio), `PuntoNoRetornoSuperadoException` |
| [16-clases-dominio-sagas-secundarias](16-clases-dominio-sagas-secundarias.png) | Dominio de las 3 sagas secundarias (`business.sagas.dominio.sagasecundariaN`): `@ValueObject` inmutables (extienden `Proceso<E>`), cada transición (`aplicarYAvanzar`, y en la secundaria2 `solicitudEnviada`/`respuestaRecibida`) devuelve instancia nueva, su constante `TIPO`, comandos/resultados, sin `version` propia |
| [17-clases-aplicacion-nucleo](17-clases-aplicacion-nucleo.png) | Aplicación, núcleo del motor (`business.ordermanager.aplicacion`): `CasoUsoContinuarOrden`/`ServicioContinuarOrden`/`ProcesadorOrden`/`SenalPaso`/`RepositorioOrden` (`buscarEjecutables` ordenado por `prioridad` DESC, luego `creada_en`, luego `proximo_reintento_en`; incluye `externalIdsFinalizadosAntesDe`/`purgarPorExternalIds`, la purga por tramitación que usan las sagas — ver 09-10 y 22) y el lease del token (reclamo, renovación por paso, reclamo de token caducado); frontera transaccional `@Transactional` con proxy auto-inyectado; `PuertoObservadorEjecucion` (SPI de observabilidad, fase 0 del plan de pruebas de carga: reclamo ganado/perdido, colisión optimista, paso completado/fallido, reintento programado, orden aparcada/finalizada); `PuertoIncidencias` (SPI genérica de incidencias operativas, la usa `ReintentoOperativo` al agotar reintentos — ver 22) |
| [18-clases-aplicacion-saga-principal](18-clases-aplicacion-saga-principal.png) | Aplicación de la saga principal (`business.sagas.aplicacion.servicio.sagaprincipal`): `ServicioSagaPrincipal` (`ProcesadorOrden`, normal + compensación; `crearHijas` propaga `orden.prioridad()` a las 3 hijas), `RepositorioOrden`, `RepositorioDatosNegocio` (carga `DatosNegocio`/documentos fuera de tx para PASO1/2/7) y `PuertoPaso1..8` |
| [19-clases-aplicacion-saga-secundaria1](19-clases-aplicacion-saga-secundaria1.png) | Aplicación de la saga secundaria 1: `ServicioSagaSecundaria1` y el puerto REST (dos métodos) |
| [20-clases-aplicacion-saga-secundaria2](20-clases-aplicacion-saga-secundaria2.png) | Aplicación de la saga secundaria 2: aparcado de 3 h, `PuertoConciliacionSecundaria2` y `ServicioRegistrarRespuestaSecundaria2` (entrada del consumer Kafka; `respuestaOk` es el único método — el evento real no tiene caso de error — con guard de idempotencia ante duplicados tardíos) |
| [21-clases-aplicacion-saga-secundaria3](21-clases-aplicacion-saga-secundaria3.png) | Aplicación de la saga secundaria 3: `ServicioSagaSecundaria3` y el puerto REST |
| [22-clases-aplicacion-soporte](22-clases-aplicacion-soporte.png) | Aplicación de soporte: `ServicioSoporteOrdenes`/`ServicioTicketsSoporte` (motor, sin cancelación ni purga: el motor solo expone la purga por tramitación genérica en `RepositorioOrden`, ver 17) + `ServicioCancelarTramitacion`/`ServicioVistaTramitacion`/`ServicioIniciarTramitacion`/`ServicioPurgarAdjuntos`/`ServicioPurgarCompletadas` (sagas, `business.sagas.aplicacion.servicio.comun`; las dos purgas con reintento operativo — `ReintentoOperativo`, distinto de `ReintentoOptimista` — y apertura de incidencia vía `PuertoIncidencias` al agotarlo) |
| [23-clases-infraestructura-persistencia](23-clases-infraestructura-persistencia.png) | Infraestructura, paquete `persistencia`: persistencia del agregado (`OrdenEntity` con columna `prioridad`/`AdaptadorRepositorioOrden`/`CandidataFila`, UNA sola tabla Oracle `orden` — negocio + ejecución fusionados desde la fase 2, sin CLOB de contexto; `buscarCandidatas` con `ORDER BY prioridad DESC, creada_en, proximo_reintento_en` sobre el índice funcional compuesto `idx_orden_candidatas`; incluye `externalIdsFinalizadosAntesDe`/`purgarPorExternalIds`, la purga por tramitación; `creada_en`/`actualizada_en` las aporta el DOMINIO — `OrdenRoot`, ver 14 —, esta entidad solo las transporta a columna, ya SIN `@PrePersist`/`@PreUpdate`: lo único que sigue siendo mecanismo de infraestructura es la marca transitoria `nueva`, `Persistable<UUID>`, para decidir `persist()` vs `merge()`) y sus 2 SPI (`MapeadorProceso` — 4 métodos: `tipo`/`estado`/`guardarContexto`/`rearmar`/`borrarContexto`, una tabla satélite por tipo —, `DescriptorSoporteOrden`); paquete `programados`, `PlanificadorContinuacion` (despierta workers si hay trabajo) |
| [24-clases-infraestructura-saga](24-clases-infraestructura-saga.png) | Infraestructura, el resto: `infraestructure.ordermanager` (`eventos` — `AdaptadorTicketsLog`, `AdaptadorObservadorLog` (implementa `PuertoObservadorEjecucion`, ver 17; log estructurado, catálogo en `src/pruebaCarga/resources/escenarios/README.md`) y `AdaptadorIncidenciasLog` (implementa `PuertoIncidencias`, ver 17) —, `programados`/`persistencia`/`ConfiguracionOrderManager`) + `infraestructure.sagas` (`ConsumidorRespuestaSecundaria2`, `ControladorTramitaciones` (REST `POST /tramitaciones`; ambos loguean su propio evento en infraestructura, sin pasar por el puerto), `SoporteSagaPrincipal`/`Secundaria1/2/3` implementando las 2 SPI de 23 con su propio repo JPA satélite, `AdaptadorBusquedaTramitacion`, `PlanificadorPurgaAdjuntos`/`PlanificadorPurgaCompletadas`, `ConfiguracionSagas`) |
| [25-clases-dominio-comun-sagas](25-clases-dominio-comun-sagas.png) | Shared kernel de las sagas (`business.sagas.dominio.comun`): `ContextoArranque` y `RefPaso1`/`RefPaso5`/`RefPaso7` — los produce la principal y los consumen las secundarias; no puede depender de él ninguna clase de `ordermanager` |
| [26-clases-datos-negocio](26-clases-datos-negocio.png) | El agregado `DatosNegocio` (`business.sagas.dominio.datosnegocio`), autocontenido: dominio (`DatosNegocio` — modela `purgadoEn`, sellado por `purgar(ahora)` con reloj determinista, igual que `creadaEn`/`actualizadaEn` en `OrdenRoot` (ver 14) —, `DatoNegocio1/2/3` — `DatoNegocio3.prioridad()` traduce el origen a `Prioridad` del motor: ORIGEN2(30) > ORIGEN1(20) > ORIGEN3(10), resto → `Prioridad.normal()`, único lugar donde vive ese mapeo —, `DocumentoNegocio`, `ExternalIdDuplicadoException`), puertos (`PuertoDatosNegocio` — REST externo, sin adaptador real — y `RepositorioDatosNegocio`, con `idsPorExternalIdsSinPurgar`/`purgarAdjuntos(id, purgadoEn)` para la purga de adjuntos, este último solo transporta a columna el valor ya sellado por el dominio), adaptador (`AdaptadorDatosNegocio`, `DatosNegocioEntity` — con `purgadoEn` — /`DocumentoNegocioEntity` — `contenido` anulable —, tablas `datos_negocio`/`datos_negocio_documento`) |

## Esquema de base de datos

El esquema, 8 tablas (9 antes de la fusión de `orden`+`proceso` en la fase 2
del refactor), se aplica manualmente desde `order-manager/db/` (un
`.sql` por objeto; orden de aplicación por FKs en su `README.md`). Ni JPA
(`ddl-auto: none`) ni ninguna herramienta de migración crean nada
automáticamente al arrancar la app. Ninguna tabla usa `ON DELETE CASCADE`
(prohibido, ver `CLAUDE.md`): los borrados de hijas los hace explícitos,
hijas antes que padre, en la misma transacción, el adaptador de persistencia.

Tablas genéricas del motor (`business.ordermanager`, sin conocer ningún
tipo de orden concreto):

- `orden` — raíz del agregado, FUSIÓN (fase 2 del refactor) de lo que antes
  eran 2 tablas (`orden` + `proceso`) con una FK entre ellas: negocio
  (`tipo`, `external_id`, `estado` de la FSM) + ejecución (intentos, lease
  del token, ticket, `completada_en`, `version`) en UNA ÚNICA fila. Antes de
  la fusión, el agregado se leía en 2 SELECT separados con la `version`
  solo en uno, lo que bajo READ_COMMITTED podía producir una lectura mixta
  (FSM vieja + ejecución fresca) y repetir un paso ya hecho; con una foto
  atómica (`findById`) eso deja de ser posible. Ya NO tiene un CLOB
  `contexto`: el contexto propio de cada tipo vive en su tabla satélite (ver
  abajo). `prioridad NUMBER(10) NOT NULL` es el metadato de planificación
  neutro (`OrdenRoot.prioridad`, ver 14): `idx_orden_candidatas` pasó a ser
  un índice funcional compuesto (`prioridad` DESC, `creada_en`,
  `proximo_reintento_en`) y `buscarCandidatas`/`buscarEjecutables` ordenan
  por él en ese mismo orden (ver 17 y 23).
- `proceso_auditoria` — intervenciones de soporte, hija de `orden` (nombre
  histórico: cuando existía la tabla `proceso` colgaba de ella; no se
  renombra para minimizar la onda expansiva del cambio).

Tablas del agregado `DatosNegocio` (`business.sagas`, ver 26), autocontenido
y sin relación de FK con `orden`, correlacionado solo por `external_id`:

- `datos_negocio` — escalares (`external_id` con índice único, para la
  idempotencia de `POST /tramitaciones`) + `purgado_en TIMESTAMP NULL`: sello
  de la purga de adjuntos (ver 09), NULL hasta que se anula el contenido de
  sus documentos.
- `datos_negocio_documento` — documentos (BLOB), hija de `datos_negocio`.
  `contenido` admite NULL: la purga de adjuntos lo anula sin borrar la fila
  (conserva `nombre`/`mime_type`).

Tablas satélite por tipo de orden (una por saga, 1:1 con `orden` por
`orden_id`, el contexto propio que antes vivía en el CLOB — ver
`MapeadorProceso` en 23; nombres históricos `proceso_saga_*`, no se
renombran por el mismo motivo que `proceso_auditoria`): `proceso_saga_principal`
(además FK a `datos_negocio` por `datosnegocio_id`), `proceso_saga_secundaria1`,
`proceso_saga_secundaria2`, `proceso_saga_secundaria3`.

## Pruebas de carga

Además de los diagramas, `order-manager` tiene un harness de pruebas de carga
multi-pod (`order-manager/src/pruebaCarga/`, task `./gradlew pruebaCarga
-Pescenario=<nombre>`) que simula N pods del motor de órdenes ejecutando en
paralelo contra la misma base de datos, con mocks de las llamadas REST
(latencia + tasa de fallo configurables) y un simulador de la respuesta
Kafka de la saga secundaria 2. No es código de producción (no aparece en
`business/**` ni `infraestructure/**` salvo el puerto de observabilidad de la
fase 0, `PuertoObservadorEjecucion`/`AdaptadorObservadorLog`, ver 17 y 24) y
no participa en `./gradlew check`; por eso sus diagramas de arquitectura no
se documentan aquí, solo se enlaza cómo usarlo.

- **Cómo lanzarlo**: `./gradlew pruebaCarga -Pescenario=<nombre>` (el nombre
  debe coincidir con un fichero `.yml` de
  `order-manager/src/pruebaCarga/resources/escenarios/`; sin `-Pescenario`
  falla con un mensaje claro). Cada ejecución escribe en
  `order-manager/build/pruebaCarga/<nombre>-<timestamp>/`: `pods.log` (log
  estructurado `clave=valor`), la H2 en fichero (`bbdd.mv.db`, consultable
  tras la prueba) e `informe.md` (veredicto BUENO/MALO, invariantes
  pasa/falla y métricas). El exit code del task es el veredicto.
- **Qué escenarios hay**: el esquema completo y la matriz de los 8
  escenarios versionados (`humo`, `base-sin-fallos`, `fallos-01/10/30`,
  `contencion-8-pods`, `humo-contencion`, `respuestas-perdidas`) están en
  `order-manager/src/pruebaCarga/resources/escenarios/README.md`, junto con
  el catálogo exacto de eventos del log (contrato que consume el
  analizador).
- **Qué mide y qué no**: detecta cuellos de botella *algorítmicos* (lote,
  intervalo, workers, política de reintentos, contención optimista). NO da
  cifras extrapolables a producción (H2 embebida no es Oracle, N pods
  comparten una máquina). Las conclusiones válidas son relativas entre
  escenarios.

## Regenerar los PNG

```bash
java -jar ~/.claude/tools/plantuml.jar -tpng -charset UTF-8 docs/*.puml
```

(O usar la skill `puml-to-png`.) Algunos diagramas de clases superan el
límite de tamaño por defecto de PlantUML (silencia el contenido que no
cabe, sin error): si un `.puml` nuevo o muy ampliado sale con un paquete
"desaparecido", regenerar con `PLANTUML_LIMIT_SIZE` más alto, p. ej.:

```bash
java -DPLANTUML_LIMIT_SIZE=16384 -jar ~/.claude/tools/plantuml.jar -tpng -charset UTF-8 docs/*.puml
```
