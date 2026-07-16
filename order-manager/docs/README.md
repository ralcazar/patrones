# Diagramas de order-manager

Diagramas PlantUML del gestor de órdenes fusionado con las sagas. Los `.puml`
y sus `.png` se versionan juntos y se actualizan en el mismo cambio que el
código que documentan (ver `CLAUDE.md` en la raíz del repo).

Modelo: un ÚNICO agregado por saga, `OrdenRoot` (ejecución: intentos, lease
del token, ticket, resultado) que contiene su `SagaRoot<E>` (negocio: FSM
`EstadoSaga*` + auditoría). No hay cola de tareas: un planificador por pod
(`PlanificadorContinuacion`) comprueba si hay trabajo (EXISTS barato) y
despierta hasta N workers pull (`TrabajadorContinuacion`, `@Async`); cada
worker reclama candidatas por lease (`continuarSiguiente`, una por pull) y la
capa de aplicación (`ServicioContinuarSaga` + un `OrquestadorSaga` por tipo
de saga) las avanza paso a paso.

## Convención de los diagramas de secuencia

Cada diagrama separa las capas en bloques (`box`), de izquierda a derecha:

| Bloque | Color | Contenido |
|---|---|---|
| Adaptadores de entrada | azul `#EFF5FB` | `PlanificadorContinuacion`/`PlanificadorTicketsSoporte`/`PlanificadorLimpieza` (`@Scheduled`), `TrabajadorContinuacion` (worker pull `@Async`), consumer Kafka, futuro REST |
| Aplicación | verde `#F5FBEF` | casos de uso, `ServicioContinuarSaga` y los `OrquestadorSaga` (uno por tipo de saga) |
| Dominio | naranja `#FBF5EF` | el agregado (`OrdenRoot` ⊃ `SagaRoot`: `SagaPrincipalRoot`/`SagaSecundariaNRoot`) |
| Adaptadores de salida | violeta `#F3EFFB` | `AdaptadorRepositorioOrden` (persistencia del agregado), puertos REST/Kafka de cada paso |

Regla de oro en todos los flujos: **dentro de la transacción solo BBDD**
(el agregado `OrdenRoot` completo: negocio + ejecución, un único `guardar`);
**fuera de ella solo I/O externo** (REST del paso, tickets). Y en los
orquestadores, **una única carga por paso**: la transacción guarda la misma
instancia cargada antes del REST (con su `version`), de modo que si otro
actor escribió entre medias el `guardar` falla por `version` y el pod se
retira (takeover seguro, sin recargas que anulen el optimistic locking).
Las flechas fluyen de izquierda a derecha y se muestran las líneas de
activación.

## Diagramas de secuencia

| Diagrama | Qué muestra |
|---|---|
| [01-arranque-saga-nueva](01-arranque-saga-nueva.png) | `ServicioIniciarTramitacion` crea el agregado (orden + `SagaPrincipalRoot`) en una tx; `PlanificadorContinuacion` descubre el trabajo (`hayTrabajoPendiente`) y despierta a los workers pull, que la reclaman con `continuarSiguiente` |
| [02-pasos-saga-principal](02-pasos-saga-principal.png) | Bucle de `ServicioContinuarSaga` + `ServicioSagaPrincipal.ejecutarPaso`: reclamo de token, REST fuera de tx y checkpoint transaccional (`resetearIntentos`+`renovarLease`) por cada uno de PASO1..PASO8 |
| [03-finalizacion-saga-principal](03-finalizacion-saga-principal.png) | Al completar PASO8: `FINALIZADA_OK` + creación de las 3 sagas hijas (`RepositorioOrden.crear` ×3) en la misma tx (sin eventos) |
| [04-saga-secundaria1](04-saga-secundaria1.png) | Saga secundaria 1: INICIO → CONFIRMACION, dos llamadas REST a métodos distintos del mismo servicio |
| [05-saga-secundaria2](05-saga-secundaria2.png) | Saga secundaria 2: solicitud REST, respuesta diferida por evento Kafka (puede tardar), aparcado de 3 h y conciliación REST si vence (nueva ventana de 3 h) |
| [06-saga-secundaria3](06-saga-secundaria3.png) | Saga secundaria 3: una única llamada REST y `FINALIZADA_OK` directo |
| [07-error-ticket-soporte](07-error-ticket-soporte.png) | Fallo de un paso: `programarReintento` con backoff 1..180 min indefinido, y el barrido `@Scheduled` que abre UN ticket cuando `intentos>=8 AND ticketAbiertoEn IS NULL` |
| [08-operaciones-soporte](08-operaciones-soporte.png) | `ServicioSoporteSagas`: consultas (CQRS), reintentar/marcarPasoOk y cancelar con compensación asíncrona (la ejecuta el mismo bucle de continuación, no el request de cancelación) |
| [09-limpieza-datos](09-limpieza-datos.png) | Purga periódica de órdenes finalizadas-bien y antiguas (`RepositorioOrden.purgarFinalizadasAntesDe`) + dedup de mensajes caducado |

## Diagramas de estado y de clases

Los diagramas de clases están troceados por subconjunto (capa + saga) para
que se lean sin cruces de líneas: uno por capa de dominio/aplicación de cada
saga, más el núcleo común, soporte e infraestructura.

| Diagrama | Qué muestra |
|---|---|
| [13-maquinas-de-estado](13-maquinas-de-estado.png) | Las 4 FSM de negocio `EstadoSagaPrincipal`/`Secundaria1`/`Secundaria2`/`Secundaria3` + `ResultadoOrden`; nota de que intentos/lease/ticket son atributos operativos de `OrdenRoot`, no una FSM (ya no hay `EstadoTicket` ni `EstadoPaso`) |
| [14-clases-dominio-comun](14-clases-dominio-comun.png) | Dominio, shared kernel: el agregado único `OrdenRoot` ⊃ `SagaRoot<E>` (una sola `version`), `PoliticaReintentos`, excepciones y VOs comunes |
| [15-clases-dominio-saga-principal](15-clases-dominio-saga-principal.png) | Dominio de la saga principal: `SagaPrincipalRoot`, comandos/resultados por paso, `ContextoTramitacion` |
| [16-clases-dominio-sagas-secundarias](16-clases-dominio-sagas-secundarias.png) | Dominio de las 3 sagas secundarias: agregados, comandos/resultados, sin `version` propia |
| [17-clases-aplicacion-nucleo](17-clases-aplicacion-nucleo.png) | Aplicación, núcleo: `CasoUsoContinuarSaga`/`ServicioContinuarSaga`/`OrquestadorSaga`/`SenalPaso`/`RepositorioOrden`/`UnidadDeTrabajo` y el lease del token (reclamo, renovación por paso, reclamo de token caducado) |
| [18-clases-aplicacion-saga-principal](18-clases-aplicacion-saga-principal.png) | Aplicación de la saga principal: `ServicioSagaPrincipal` (normal + compensación), `RepositorioOrden` y `PuertoPaso1..8` |
| [19-clases-aplicacion-saga-secundaria1](19-clases-aplicacion-saga-secundaria1.png) | Aplicación de la saga secundaria 1: `ServicioSagaSecundaria1` y el puerto REST (dos métodos) |
| [20-clases-aplicacion-saga-secundaria2](20-clases-aplicacion-saga-secundaria2.png) | Aplicación de la saga secundaria 2: aparcado de 3 h, `PuertoConciliacionSecundaria2` y `ServicioRegistrarRespuestaSecundaria2` (entrada del consumer Kafka) |
| [21-clases-aplicacion-saga-secundaria3](21-clases-aplicacion-saga-secundaria3.png) | Aplicación de la saga secundaria 3: `ServicioSagaSecundaria3` y el puerto REST |
| [22-clases-aplicacion-soporte](22-clases-aplicacion-soporte.png) | Aplicación de soporte: `ServicioSoporteSagas` (intervenciones + consultas CQRS), `ServicioTicketsSoporte` (ticket único vía log) y `ServicioLimpiezaDatos` |
| [23-clases-infraestructura-persistencia](23-clases-infraestructura-persistencia.png) | Infraestructura: persistencia del agregado (`OrdenEntity`/`SagaEntity`/`AdaptadorRepositorioOrden`/`CandidataFila`, tablas Oracle `orden`/`saga`) y `PlanificadorContinuacion` (despierta workers si hay trabajo) |
| [24-clases-infraestructura-saga](24-clases-infraestructura-saga.png) | Infraestructura: el resto de adaptadores — `ConsumidorRespuestaSecundaria2`, `TrabajadorContinuacion` (worker pull) + `ConfiguracionEjecucionAsincrona` (pool "ejecutorContinuacion"), `PlanificadorLimpieza`/`PlanificadorTicketsSoporte`, `AdaptadorTicketsLog`, `AdaptadorConsultaSagasSoporte`, `AdaptadorSagasTicketPendiente` |

## Esquema de base de datos

El esquema (tablas `saga`, `saga_auditoria`, `orden`) se aplica manualmente
desde `order-manager/db/` (un `.sql` por objeto; orden de aplicación por FKs
en su `README.md`). Ni JPA (`ddl-auto: none`) ni ninguna herramienta de
migración crean nada automáticamente al arrancar la app.

## Regenerar los PNG

```bash
java -jar ~/.claude/tools/plantuml.jar -tpng -charset UTF-8 docs/*.puml
```

(O usar la skill `puml-to-png`.)
