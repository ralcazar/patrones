# Diagramas de order-manager

Diagramas PlantUML del motor de órdenes (`business.ordermanager` /
`infraestructure.ordermanager`) y de las sagas concretas construidas sobre él
(`business.sagas` / `infraestructure.sagas`). Los `.puml` y sus `.png` se
versionan juntos y se actualizan en el mismo cambio que el código que
documentan (ver `CLAUDE.md` en la raíz del repo).

Modelo: un ÚNICO agregado por orden, `OrdenRoot` (ejecución: intentos, lease
del token, ticket, completadaEn) que contiene su `Proceso<E>` (entidad interna,
negocio: FSM `EstadoSaga*` + auditoría). `Proceso<E>` y `OrdenRoot` son
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

## Convención de los diagramas de secuencia

Cada diagrama separa las capas en bloques (`box`), de izquierda a derecha:

| Bloque | Color | Contenido |
|---|---|---|
| Adaptadores de entrada | azul `#EFF5FB` | `PlanificadorContinuacion`/`PlanificadorTicketsSoporte`/`PlanificadorLimpieza`/`PlanificadorPurgaDatosNegocio` (`@Scheduled`), `TrabajadorContinuacion` (worker pull `@Async`), consumer Kafka, `ControladorTramitaciones` (REST `POST /tramitaciones`) |
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
transacción sin REST intercalado (`ServicioIniciarTramitacion`,
`ServicioLimpiezaDatos`, `ServicioSoporteOrdenes`,
`ServicioCancelarTramitacion`, `ServicioRegistrarRespuestaSecundaria2`)
anotan el método público directamente, sin necesidad de `self`.
Las flechas fluyen de izquierda a derecha y se muestran las líneas de
activación.

## Diagramas de secuencia

| Diagrama | Qué muestra |
|---|---|
| [01-arranque-saga-nueva](01-arranque-saga-nueva.png) | `POST /tramitaciones` (`ControladorTramitaciones`): idempotente vía `PuertoBusquedaTramitacion`; si no existe, `PuertoDatosNegocio.obtener` fuera de tx y `ServicioIniciarTramitacion.crearAgregados` (`@Transactional`) crea `DatosNegocio` + `SagaPrincipal` + `OrdenRoot` (con el camino 502 si el servicio externo falla y el de carrera si el índice único de `datos_negocio.external_id` lo rechaza); `PlanificadorContinuacion` descubre el trabajo (`hayTrabajoPendiente`) y despierta a los workers pull, que la reclaman con `continuarSiguiente` |
| [02-pasos-saga-principal](02-pasos-saga-principal.png) | Bucle de `ServicioContinuarOrden` + `ServicioSagaPrincipal.ejecutarPaso` (`ProcesadorOrden`): reclamo de token, REST fuera de tx y checkpoint transaccional (`resetearIntentos`+`renovarLease`) por cada uno de PASO1..PASO8 |
| [03-finalizacion-saga-principal](03-finalizacion-saga-principal.png) | Al completar PASO8: `orden.finalizar(ahora)` (`completadaEn`) + creación de las 3 sagas hijas (`RepositorioOrden.crear` ×3) en la misma tx (sin eventos) |
| [04-saga-secundaria1](04-saga-secundaria1.png) | Saga secundaria 1: INICIO → CONFIRMACION, dos llamadas REST a métodos distintos del mismo servicio |
| [05-saga-secundaria2](05-saga-secundaria2.png) | Saga secundaria 2: solicitud REST, respuesta diferida por evento Kafka (puede tardar), aparcado de 3 h y conciliación REST si vence (nueva ventana de 3 h) |
| [06-saga-secundaria3](06-saga-secundaria3.png) | Saga secundaria 3: una única llamada REST y `orden.finalizar(ahora)` directo |
| [07-error-ticket-soporte](07-error-ticket-soporte.png) | Fallo de un paso: `programarReintento` con backoff 1..180 min indefinido guardando `DetalleError` (clase+mensaje de la excepción, sin stacktrace) en `OrdenRoot.ultimoError`, y el barrido `@Scheduled` que abre UN ticket cuando `intentos>=8 AND ticketAbiertoEn IS NULL`, incluyendo ese detalle en el log del ticket |
| [08-operaciones-soporte](08-operaciones-soporte.png) | `ServicioSoporteOrdenes` (motor): consultas (CQRS), reintentar/marcarPasoOk; `ServicioCancelarTramitacion` (sagas): cancelar con compensación asíncrona (la ejecuta el mismo bucle de continuación, no el request de cancelación) |
| [09-limpieza-datos](09-limpieza-datos.png) | Purga periódica de órdenes finalizadas (`completadaEn` no nula) y antiguas (`RepositorioOrden.purgarFinalizadasAntesDe`, sin `ON DELETE CASCADE`: borrado explícito hijas→padre) + dedup de mensajes caducado |

## Diagramas de estado y de clases

Los diagramas de clases están troceados por subconjunto (capa + saga) para
que se lean sin cruces de líneas: uno por capa de dominio/aplicación de cada
saga, más el núcleo del motor, el shared kernel de sagas, soporte e
infraestructura.

| Diagrama | Qué muestra |
|---|---|
| [13-maquinas-de-estado](13-maquinas-de-estado.png) | Las 4 FSM de negocio `EstadoSagaPrincipal`/`Secundaria1`/`Secundaria2`/`Secundaria3` + el estado operativo de finalización `completadaEn`; nota de que intentos/lease/ticket son atributos operativos de `OrdenRoot`, no una FSM (ya no hay `EstadoTicket` ni `EstadoPaso`) |
| [14-clases-dominio-ordermanager](14-clases-dominio-ordermanager.png) | Dominio del motor genérico (`business.ordermanager.dominio`): el agregado único `OrdenRoot` ⊃ `Proceso<E>` (entidad interna, una sola `version` la controla el agregado), `TipoOrden` (VO abierto), `PoliticaReintentos`, `DetalleError` (clase+mensaje del último fallo, para soporte), excepciones y VOs del motor — sin ninguna clase de sagas |
| [15-clases-dominio-saga-principal](15-clases-dominio-saga-principal.png) | Dominio de la saga principal (`business.sagas.dominio.sagaprincipal`): `SagaPrincipal` (entidad, extiende `Proceso<EstadoSagaPrincipal>`), su constante `TIPO`, comandos/resultados por paso, `ContextoTramitacion` (referencia `DatosNegocioId` — ver 26 — en vez de contener datos de negocio), `PuntoNoRetornoSuperadoException` |
| [16-clases-dominio-sagas-secundarias](16-clases-dominio-sagas-secundarias.png) | Dominio de las 3 sagas secundarias (`business.sagas.dominio.sagasecundariaN`): entidades (extienden `Proceso<E>`), su constante `TIPO`, comandos/resultados, sin `version` propia |
| [17-clases-aplicacion-nucleo](17-clases-aplicacion-nucleo.png) | Aplicación, núcleo del motor (`business.ordermanager.aplicacion`): `CasoUsoContinuarOrden`/`ServicioContinuarOrden`/`ProcesadorOrden`/`SenalPaso`/`RepositorioOrden` y el lease del token (reclamo, renovación por paso, reclamo de token caducado); frontera transaccional `@Transactional` con proxy auto-inyectado; `PuertoObservadorEjecucion` (SPI de observabilidad, fase 0 del plan de pruebas de carga: reclamo ganado/perdido, colisión optimista, paso completado/fallido, reintento programado, orden aparcada/finalizada) |
| [18-clases-aplicacion-saga-principal](18-clases-aplicacion-saga-principal.png) | Aplicación de la saga principal (`business.sagas.aplicacion.servicio.sagaprincipal`): `ServicioSagaPrincipal` (`ProcesadorOrden`, normal + compensación), `RepositorioOrden`, `RepositorioDatosNegocio` (carga `DatosNegocio`/documentos fuera de tx para PASO1/2/7) y `PuertoPaso1..8` |
| [19-clases-aplicacion-saga-secundaria1](19-clases-aplicacion-saga-secundaria1.png) | Aplicación de la saga secundaria 1: `ServicioSagaSecundaria1` y el puerto REST (dos métodos) |
| [20-clases-aplicacion-saga-secundaria2](20-clases-aplicacion-saga-secundaria2.png) | Aplicación de la saga secundaria 2: aparcado de 3 h, `PuertoConciliacionSecundaria2` y `ServicioRegistrarRespuestaSecundaria2` (entrada del consumer Kafka) |
| [21-clases-aplicacion-saga-secundaria3](21-clases-aplicacion-saga-secundaria3.png) | Aplicación de la saga secundaria 3: `ServicioSagaSecundaria3` y el puerto REST |
| [22-clases-aplicacion-soporte](22-clases-aplicacion-soporte.png) | Aplicación de soporte: `ServicioSoporteOrdenes`/`ServicioTicketsSoporte`/`ServicioLimpiezaDatos` (motor, sin cancelación; `ServicioLimpiezaDatos` emite `datosAntiguosPurgados` por `PuertoObservadorEjecucion`, ver 17) + `ServicioCancelarTramitacion`/`ServicioVistaTramitacion`/`ServicioIniciarTramitacion`/`ServicioPurgarDatosNegocioHuerfanos` (sagas, `business.sagas.aplicacion.servicio.comun`) |
| [23-clases-infraestructura-persistencia](23-clases-infraestructura-persistencia.png) | Infraestructura, paquete `persistencia`: persistencia del agregado (`OrdenEntity`/`ProcesoEntity`/`AdaptadorRepositorioOrden`/`CandidataFila`, tablas Oracle `orden`/`proceso`, sin CLOB de contexto) y sus 2 SPI (`MapeadorProceso` — 4 métodos: `tipo`/`estado`/`guardarContexto`/`rearmar`/`borrarContexto`, una tabla satélite por tipo —, `DescriptorSoporteOrden`); paquete `programados`, `PlanificadorContinuacion` (despierta workers si hay trabajo) |
| [24-clases-infraestructura-saga](24-clases-infraestructura-saga.png) | Infraestructura, el resto: `infraestructure.ordermanager` (`eventos` — `AdaptadorTicketsLog` y `AdaptadorObservadorLog` (implementa `PuertoObservadorEjecucion`, ver 17; log estructurado, catálogo en `src/pruebaCarga/resources/escenarios/README.md`) —, `programados`/`persistencia`/`ConfiguracionOrderManager`) + `infraestructure.sagas` (`ConsumidorRespuestaSecundaria2`, `ControladorTramitaciones` (REST `POST /tramitaciones`; ambos loguean su propio evento en infraestructura, sin pasar por el puerto), `SoporteSagaPrincipal`/`Secundaria1/2/3` implementando las 2 SPI de 23 con su propio repo JPA satélite, `AdaptadorBusquedaTramitacion`, `PlanificadorPurgaDatosNegocio`, `ConfiguracionSagas`) |
| [25-clases-dominio-comun-sagas](25-clases-dominio-comun-sagas.png) | Shared kernel de las sagas (`business.sagas.dominio.comun`): `ContextoArranque` y `RefPaso1`/`RefPaso5`/`RefPaso7` — los produce la principal y los consumen las secundarias; no puede depender de él ninguna clase de `ordermanager` |
| [26-clases-datos-negocio](26-clases-datos-negocio.png) | El agregado `DatosNegocio` (`business.sagas.dominio.datosnegocio`), autocontenido: dominio (`DatosNegocio`, `DatoNegocio1/2/3`, `DocumentoNegocio`, `ExternalIdDuplicadoException`), puertos (`PuertoDatosNegocio` — REST externo, sin adaptador real — y `RepositorioDatosNegocio`), adaptador (`AdaptadorDatosNegocio`, `DatosNegocioEntity`/`DocumentoNegocioEntity`, tablas `datos_negocio`/`datos_negocio_documento`) |

## Esquema de base de datos

El esquema, 9 tablas, se aplica manualmente desde `order-manager/db/` (un
`.sql` por objeto; orden de aplicación por FKs en su `README.md`). Ni JPA
(`ddl-auto: none`) ni ninguna herramienta de migración crean nada
automáticamente al arrancar la app. Ninguna tabla usa `ON DELETE CASCADE`
(prohibido, ver `CLAUDE.md`): los borrados de hijas los hace explícitos,
hijas antes que padre, en la misma transacción, el adaptador de persistencia.

Tablas genéricas del motor (`business.ordermanager`, sin conocer ningún
tipo de orden concreto):

- `proceso` — FSM común a todos los tipos (`orden_id` PK, `tipo`,
  `external_id`, `estado`); ya NO tiene un CLOB `contexto`: el contexto
  propio de cada tipo vive en su tabla satélite (ver abajo).
- `proceso_auditoria` — intervenciones de soporte, hija de `proceso`.
- `orden` — estado de ejecución (intentos, lease del token, ticket,
  `completada_en`, `version`), hija/FK 1:1 de `proceso`.

Tablas del agregado `DatosNegocio` (`business.sagas`, ver 26), autocontenido
y sin relación de FK con `proceso`, correlacionado solo por `external_id`:

- `datos_negocio` — escalares (`external_id` con índice único, para la
  idempotencia de `POST /tramitaciones`).
- `datos_negocio_documento` — documentos (BLOB), hija de `datos_negocio`.

Tablas satélite por tipo de orden (una por saga, 1:1 con `proceso` por
`orden_id`, el contexto propio que antes vivía en el CLOB — ver
`MapeadorProceso` en 23): `proceso_saga_principal` (además FK a
`datos_negocio` por `datosnegocio_id`), `proceso_saga_secundaria1`,
`proceso_saga_secundaria2`, `proceso_saga_secundaria3`.

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
