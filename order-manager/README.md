# order-manager (Java 21 + jMolecules)

Motor de órdenes genérico (`ordermanager`) sobre el que se construyen 4 sagas
concretas (`sagas`): saga principal `PASO1 → PASO2 → ... → PASO8` y, al
completar el PASO8, arrancan 3 sagas secundarias independientes sin join:
`SECUNDARIA1` (dos llamadas REST encadenadas al mismo servicio), `SECUNDARIA2`
(una llamada REST cuya respuesta llega después como evento Kafka, puede
tardar; si en 3 h no llega se consulta el REST de conciliación y, si sigue
sin resultado, se abre otra ventana de 3 h) y `SECUNDARIA3` (una llamada
REST). Punto de no retorno en PASO7 de la principal.

Fallos: backoff exponencial en minutos `1, 3, 5, 10, 20, 45, 90, 180`;
consumida la escalera (intentos >= 8) se sigue reintentando indefinidamente
cada 180 min, y la orden queda pendiente de ticket. Los tickets no se abren
en línea: un `@Scheduled` (cada 3 h, de 8 a 17) barre las órdenes con la
escalera consumida y sin ticket abierto, abre UN único ticket para todas
(escribir un texto en el log: no hay id de ticket) y las marca con la fecha
de apertura (`ticketAbiertoEn`); un reintento que acaba bien la limpia, así
que un atasco posterior abre un ticket NUEVO. Soporte puede cancelar (solo la
principal, solo pre-PASO7, arranca la compensación), reintentar, marcar-OK
manual y consultar/filtrar las órdenes — viendo el marcador de ticket, la
fecha del próximo reintento y si el paso pendiente requiere datos manuales.
Un planificador purga periódicamente los datos antiguos que acabaron bien
(`ServicioLimpiezaDatos`). **Todo termina cuando las sagas se completan: no
se publican eventos de salida.**

No hay órdenes "padre" ni "hijas" como concepto de infraestructura: cada
saga es su propio agregado `OrdenRoot` (ejecución) que contiene su propio
`Proceso<E>` (negocio; sus 4 subclases son `SagaPrincipal` y
`SagaSecundaria1/2/3`). Que al completarse la principal arranquen las 3
secundarias es una decisión de `ServicioSagaPrincipal` (crea 3 agregados
nuevos en la misma transacción que finaliza el suyo); la saga arrancada nace
independiente, con su contexto recortado (`ContextoArranque`), y no conserva
ningún vínculo con la que la originó: la única correlación es el
**`externalId`** (UUID único por tramitación, presente en las 4 sagas).

En [`docs/`](docs/README.md) hay diagramas de secuencia y de clases
PlantUML de cada funcionalidad, con las capas en bloques de izquierda a
derecha (adaptadores de entrada → aplicación → dominio → adaptadores de
salida) y líneas de activación. Se mantienen sincronizados con el código
(ver `CLAUDE.md`).

## La idea: motor genérico + sagas concretas encima

**Cada orden = una ejecución paso a paso con reintentos.** El agregado
`OrdenRoot` es la cola de tareas transaccional del motor — y es dominio
puro (`business.ordermanager.dominio`), no infraestructura: no hay
scheduler externo (Quartz, DB-scheduler); el "próximo reintento" es un
campo (`proximoReintentoEn`) que un planificador ligero (`@Scheduled` +
worker pull `@Async`) consulta con un `SELECT` barato.

**Regla de oro:** dentro de la transacción solo BBDD (agregado completo:
negocio + ejecución, un único `guardar`); fuera de ella solo I/O externo
(REST del paso, tickets). Si el proceso muere en cualquier punto, el lease
del token (`ordermanager.lease`) libera la orden para que otro pod la
reclame, y `ServicioContinuarOrden` reanuda desde el último paso confirmado
(carga única antes del REST, misma instancia al guardar o reintentar: ver
`docs/README.md`).

## Estructura (business = Java puro + jMolecules; infraestructure = Spring)

```
com/ejemplo/app/
  business/
    ordermanager/                 SOLO JDK + jMolecules; NO conoce las sagas
      dominio/          OrdenRoot (@AggregateRoot), Proceso<E> (@Entity, base
                        de las sagas), TipoOrden (VO abierto), OrdenId,
                        PoliticaReintentos, ResultadoOrden, ComandoPaso/
                        ResultadoPaso (marcadoras), excepciones, VOs.
      aplicacion/
        puerto/entrada/  CasoUsoContinuarOrden, CasoUsoIntervenirOrden,
                         CasoUsoConsultarOrdenesSoporte,
                         CasoUsoAbrirTicketsPendientes,
                         CasoUsoLimpiarDatosAntiguos
        puerto/salida/   RepositorioOrden, PuertoConsultaOrdenesSoporte,
                         PuertoOrdenesTicketPendiente, PuertoTicketsSoporte
        servicio/        ServicioContinuarOrden, ProcesadorOrden (SPI, una
                         implementación por tipo de orden), SenalPaso,
                         ServicioSoporteOrdenes, ServicioTicketsSoporte,
                         ServicioLimpiezaDatos
    sagas/                         Aquí "saga" es vocabulario correcto
      dominio/comun/     ContextoArranque, RefPaso1/5/7 (shared kernel de
                        las 4 sagas)
      dominio/sagaprincipal/      SagaPrincipal (extends Proceso<...>, TIPO),
                                  ComandoPasoPrincipal, ResultadoPasoPrincipal,
                                  ContextoTramitacion, RefPaso2/3/4/6/8,
                                  DatoNegocio2/3, PuntoNoRetornoSuperadoException
      dominio/sagasecundaria1/2/3/  SagaSecundariaN: sus FSM, comandos/resultados
      aplicacion/puerto/entrada/  CasoUsoIniciarTramitacion,
                                  CasoUsoCancelarTramitacion,
                                  CasoUsoVistaTramitacion,
                                  CasoUsoRegistrarRespuestaSecundaria2
      aplicacion/puerto/salida/   PuertoPaso1..8, PuertoSagaSecundaria1/2/3,
                                  PuertoConciliacionSecundaria2
      aplicacion/servicio/comun/  ServicioIniciarTramitacion,
                                  ServicioCancelarTramitacion,
                                  ServicioVistaTramitacion,
                                  ServicioRegistrarRespuestaSecundaria2
      aplicacion/servicio/sagaprincipal|sagasecundariaN/
                                  ServicioSagaPrincipal / ServicioSagaSecundariaN
                                  (implementan ProcesadorOrden del motor)
  infraestructure/
    ordermanager/                 Spring, JPA, wiring del motor genérico
      persistencia/     OrdenEntity/ProcesoEntity (@Entity), sus JpaRepository,
                        AdaptadorRepositorioOrden, MapeadorProceso y
                        DescriptorSoporteOrden (SPI por tipo),
                        AdaptadorConsultaOrdenesSoporte,
                        AdaptadorOrdenesTicketPendiente, ContextoCodec
      eventos/          AdaptadorTicketsLog
      programados/      PlanificadorContinuacion, TrabajadorContinuacion,
                        PlanificadorLimpieza, PlanificadorTicketsSoporte,
                        ConfiguracionEjecucionAsincrona
      ConfiguracionOrderManager.java
    sagas/                         Spring, Kafka, Jackson; wiring de las 4 sagas
      persistencia/     SoporteSagaPrincipal/Secundaria1/2/3 (implementan
                        MapeadorProceso + DescriptorSoporteOrden), AyudanteContexto
      eventos/          ConsumidorRespuestaSecundaria2 (único uso de Kafka)
      ConfiguracionSagas.java
  OrderManagerApplication.java
resources/application.yml   claves ordermanager.* (motor) y sagas.* (concretas)
db/                          DDL manual de Oracle: proceso.sql, proceso_auditoria.sql, orden.sql
```

Build: Gradle (`./gradlew build`). El test `ReglasArquitecturaTest`
(ArchUnit) verifica que `business/**` no depende de Spring/JPA/Jackson/Kafka,
que `infraestructure/**` no es importado desde `business/**`, y que
**`ordermanager` no depende de `sagas`** (ni en producción ni en tests) ni
usa vocabulario "saga" en sus propias clases — ver la sección
correspondiente en `CLAUDE.md` de la raíz del repo.

## Los 3 puntos de extensión del motor (SPI)

Una aplicación que reutilice `ordermanager` con otro tipo de orden solo
necesita implementar estas 3 interfaces y registrarlas como beans Spring
(`List<...>` inyectado, indexado por `tipo()`); el motor no cambia:

- **`ProcesadorOrden`** (`business.ordermanager.aplicacion.servicio`): ejecuta
  un paso — `tipo()` + `ejecutarPaso(OrdenRoot)`.
- **`MapeadorProceso`** (`infraestructure.ordermanager.persistencia`):
  (des)arma la forma persistible (`estado`, `contexto`) de un `Proceso<?>`.
- **`DescriptorSoporteOrden`** (`infraestructure.ordermanager.persistencia`):
  paso pendiente / si requiere datos manuales / si es cancelable, a partir de
  `(tipo, estado)`, para la pantalla de soporte sin cargar agregados.

Las 4 sagas de este proyecto son el único consumidor de las 3 SPI hoy; ver
`order-manager/docs/README.md` (diagramas 14, 17, 23, 24-25) para el detalle
completo.

## Idempotencia

La reentrega por lease implica at-least-once hacia los servicios externos:
si un paso quedó en curso sin resultado registrado, el siguiente
`continuarSiguiente()` lo re-ejecuta. Los servicios REST de los pasos deben
ser idempotentes por `externalId` (o tolerar el duplicado). El consumer de
Kafka de la saga secundaria 2 no deduplica por `mensajeId`: el evento real
solo trae éxito y `respuestaRecibida` es una transición a TERMINADA (estado
absorbente), así que un duplicado reentregado por Kafka llega a una orden ya
terminada (o purgada) y el guard de
`ServicioRegistrarRespuestaSecundaria2.respuestaOk` lo ignora sin reescribir
nada.

## Pendiente de implementar (infra restante)

- Adaptadores REST de `PuertoPaso1..8`, `PuertoSagaSecundaria1/2/3` y
  `PuertoConciliacionSecundaria2` (fallo → `ExcepcionServicioExterno`).
- Adaptador REST del backoffice sobre `CasoUsoIntervenirOrden`,
  `CasoUsoConsultarOrdenesSoporte`, `CasoUsoCancelarTramitacion` y
  `CasoUsoVistaTramitacion` (mapear `PuntoNoRetornoSuperadoException` → 409;
  el paso viaja por nombre, opcional).
