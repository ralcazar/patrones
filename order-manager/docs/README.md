# Diagramas de order-manager

Diagramas PlantUML del motor de órdenes (`business.ordermanager` /
`infraestructure.ordermanager`) y de las sagas concretas construidas sobre él
(`business.sagas` / `infraestructure.sagas`). Los `.puml` y sus `.png` se
versionan juntos y se actualizan en el mismo cambio que el código que
documentan (ver `CLAUDE.md` en la raíz del repo).

Modelo: un ÚNICO agregado por orden, `OrdenRoot` (ejecución: intentos, lease
del token, ticket, resultado) que contiene su `Proceso<E>` (entidad interna,
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
| `MapeadorProceso` | `infraestructure.ordermanager.persistencia` | (Des)arma la forma persistible de un `Proceso<?>` por tipo (ver 23) |
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
| Adaptadores de entrada | azul `#EFF5FB` | `PlanificadorContinuacion`/`PlanificadorTicketsSoporte`/`PlanificadorLimpieza` (`@Scheduled`), `TrabajadorContinuacion` (worker pull `@Async`), consumer Kafka, futuro REST |
| Aplicación | verde `#F5FBEF` | casos de uso, `ServicioContinuarOrden` y los `ProcesadorOrden` de cada saga (`ServicioSagaPrincipal`/`Secundaria1/2/3`) |
| Dominio | naranja `#FBF5EF` | el agregado (`OrdenRoot` ⊃ `Proceso`: `SagaPrincipal`/`SagaSecundariaN`) |
| Adaptadores de salida | violeta `#F3EFFB` | `AdaptadorRepositorioOrden` (persistencia del agregado), puertos REST/Kafka de cada paso |

Regla de oro en todos los flujos: **dentro de la transacción solo BBDD**
(el agregado `OrdenRoot` completo: negocio + ejecución, un único `guardar`);
**fuera de ella solo I/O externo** (REST del paso, tickets). Y en el bucle de
`ServicioContinuarOrden`, **una única carga por paso**: carga el agregado
antes del REST y se lo pasa ya cargado al `ProcesadorOrden`
(`ejecutarPaso(orden)`); tanto la transacción que cierra el paso como, si el
REST falla, la que programa el reintento guardan esa MISMA instancia (con su
`version`), de modo que si otro actor escribió entre medias el `guardar`
falla por `version` y el pod se retira (takeover seguro, sin recargas que
anulen el optimistic locking).

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
| [01-arranque-saga-nueva](01-arranque-saga-nueva.png) | `ServicioIniciarTramitacion` (business.sagas) crea el agregado (orden + `SagaPrincipal`) en una tx; `PlanificadorContinuacion` descubre el trabajo (`hayTrabajoPendiente`) y despierta a los workers pull, que la reclaman con `continuarSiguiente` |
| [02-pasos-saga-principal](02-pasos-saga-principal.png) | Bucle de `ServicioContinuarOrden` + `ServicioSagaPrincipal.ejecutarPaso` (`ProcesadorOrden`): reclamo de token, REST fuera de tx y checkpoint transaccional (`resetearIntentos`+`renovarLease`) por cada uno de PASO1..PASO8 |
| [03-finalizacion-saga-principal](03-finalizacion-saga-principal.png) | Al completar PASO8: `FINALIZADA_OK` + creación de las 3 sagas hijas (`RepositorioOrden.crear` ×3) en la misma tx (sin eventos) |
| [04-saga-secundaria1](04-saga-secundaria1.png) | Saga secundaria 1: INICIO → CONFIRMACION, dos llamadas REST a métodos distintos del mismo servicio |
| [05-saga-secundaria2](05-saga-secundaria2.png) | Saga secundaria 2: solicitud REST, respuesta diferida por evento Kafka (puede tardar), aparcado de 3 h y conciliación REST si vence (nueva ventana de 3 h) |
| [06-saga-secundaria3](06-saga-secundaria3.png) | Saga secundaria 3: una única llamada REST y `FINALIZADA_OK` directo |
| [07-error-ticket-soporte](07-error-ticket-soporte.png) | Fallo de un paso: `programarReintento` con backoff 1..180 min indefinido, y el barrido `@Scheduled` que abre UN ticket cuando `intentos>=8 AND ticketAbiertoEn IS NULL` |
| [08-operaciones-soporte](08-operaciones-soporte.png) | `ServicioSoporteOrdenes` (motor): consultas (CQRS), reintentar/marcarPasoOk; `ServicioCancelarTramitacion` (sagas): cancelar con compensación asíncrona (la ejecuta el mismo bucle de continuación, no el request de cancelación) |
| [09-limpieza-datos](09-limpieza-datos.png) | Purga periódica de órdenes finalizadas-bien y antiguas (`RepositorioOrden.purgarFinalizadasAntesDe`) + dedup de mensajes caducado |

## Diagramas de estado y de clases

Los diagramas de clases están troceados por subconjunto (capa + saga) para
que se lean sin cruces de líneas: uno por capa de dominio/aplicación de cada
saga, más el núcleo del motor, el shared kernel de sagas, soporte e
infraestructura.

| Diagrama | Qué muestra |
|---|---|
| [13-maquinas-de-estado](13-maquinas-de-estado.png) | Las 4 FSM de negocio `EstadoSagaPrincipal`/`Secundaria1`/`Secundaria2`/`Secundaria3` + `ResultadoOrden`; nota de que intentos/lease/ticket son atributos operativos de `OrdenRoot`, no una FSM (ya no hay `EstadoTicket` ni `EstadoPaso`) |
| [14-clases-dominio-ordermanager](14-clases-dominio-ordermanager.png) | Dominio del motor genérico (`business.ordermanager.dominio`): el agregado único `OrdenRoot` ⊃ `Proceso<E>` (entidad interna, una sola `version` la controla el agregado), `TipoOrden` (VO abierto), `PoliticaReintentos`, excepciones y VOs del motor — sin ninguna clase de sagas |
| [15-clases-dominio-saga-principal](15-clases-dominio-saga-principal.png) | Dominio de la saga principal (`business.sagas.dominio.sagaprincipal`): `SagaPrincipal` (entidad, extiende `Proceso<EstadoSagaPrincipal>`), su constante `TIPO`, comandos/resultados por paso, `ContextoTramitacion`, `PuntoNoRetornoSuperadoException` |
| [16-clases-dominio-sagas-secundarias](16-clases-dominio-sagas-secundarias.png) | Dominio de las 3 sagas secundarias (`business.sagas.dominio.sagasecundariaN`): entidades (extienden `Proceso<E>`), su constante `TIPO`, comandos/resultados, sin `version` propia |
| [17-clases-aplicacion-nucleo](17-clases-aplicacion-nucleo.png) | Aplicación, núcleo del motor (`business.ordermanager.aplicacion`): `CasoUsoContinuarOrden`/`ServicioContinuarOrden`/`ProcesadorOrden`/`SenalPaso`/`RepositorioOrden` y el lease del token (reclamo, renovación por paso, reclamo de token caducado); frontera transaccional `@Transactional` con proxy auto-inyectado |
| [18-clases-aplicacion-saga-principal](18-clases-aplicacion-saga-principal.png) | Aplicación de la saga principal (`business.sagas.aplicacion.servicio.sagaprincipal`): `ServicioSagaPrincipal` (`ProcesadorOrden`, normal + compensación), `RepositorioOrden` y `PuertoPaso1..8` |
| [19-clases-aplicacion-saga-secundaria1](19-clases-aplicacion-saga-secundaria1.png) | Aplicación de la saga secundaria 1: `ServicioSagaSecundaria1` y el puerto REST (dos métodos) |
| [20-clases-aplicacion-saga-secundaria2](20-clases-aplicacion-saga-secundaria2.png) | Aplicación de la saga secundaria 2: aparcado de 3 h, `PuertoConciliacionSecundaria2` y `ServicioRegistrarRespuestaSecundaria2` (entrada del consumer Kafka) |
| [21-clases-aplicacion-saga-secundaria3](21-clases-aplicacion-saga-secundaria3.png) | Aplicación de la saga secundaria 3: `ServicioSagaSecundaria3` y el puerto REST |
| [22-clases-aplicacion-soporte](22-clases-aplicacion-soporte.png) | Aplicación de soporte: `ServicioSoporteOrdenes`/`ServicioTicketsSoporte`/`ServicioLimpiezaDatos` (motor, sin cancelación) + `ServicioCancelarTramitacion`/`ServicioVistaTramitacion` (sagas, extraídos de lo que antes era `ServicioSoporteSagas`) |
| [23-clases-infraestructura-persistencia](23-clases-infraestructura-persistencia.png) | Infraestructura, paquete `persistencia`: persistencia del agregado (`OrdenEntity`/`ProcesoEntity`/`AdaptadorRepositorioOrden`/`CandidataFila`, tablas Oracle `orden`/`proceso`) y sus 2 SPI (`MapeadorProceso`, `DescriptorSoporteOrden`); paquete `programados`, `PlanificadorContinuacion` (despierta workers si hay trabajo) |
| [24-clases-infraestructura-saga](24-clases-infraestructura-saga.png) | Infraestructura, el resto: `infraestructure.ordermanager` (`eventos`/`programados`/`persistencia`/`ConfiguracionOrderManager`) + `infraestructure.sagas` (`ConsumidorRespuestaSecundaria2`, `SoporteSagaPrincipal`/`Secundaria1/2/3` implementando las 2 SPI de 23, `ConfiguracionSagas`) |
| [25-clases-dominio-comun-sagas](25-clases-dominio-comun-sagas.png) | Shared kernel de las sagas (`business.sagas.dominio.comun`): `ContextoArranque` y `RefPaso1`/`RefPaso5`/`RefPaso7` — los produce la principal y los consumen las secundarias; no puede depender de él ninguna clase de `ordermanager` |

## Esquema de base de datos

El esquema (tablas `proceso`, `proceso_auditoria`, `orden`) se aplica
manualmente desde `order-manager/db/` (un `.sql` por objeto; orden de
aplicación por FKs en su `README.md`). Ni JPA (`ddl-auto: none`) ni ninguna
herramienta de migración crean nada automáticamente al arrancar la app.

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
