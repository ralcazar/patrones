# Sagas + GestorOrdenes fusionados (Java 21 + jMolecules)

Flujo: saga principal `PASO1 → PASO2 → ... → PASO8` y, al completar el PASO8,
arrancan 3 sagas secundarias independientes sin join: `SECUNDARIA1` (dos
llamadas REST encadenadas al mismo servicio), `SECUNDARIA2` (una llamada REST
cuya respuesta llega después como evento Kafka, puede tardar; si en 24 h no
llega se consulta el REST de conciliación y, si sigue sin resultado, se abre
otra ventana de 24 h) y `SECUNDARIA3` (una llamada REST). Punto de no retorno
en PASO7.

Fallos: backoff exponencial en minutos `1, 3, 5, 10, 20, 45, 90, 180`;
consumida la escalera se sigue reintentando indefinidamente cada 180 min pero
la saga queda con "abrir ticket pendiente" (el flag se borra si un reintento
acaba bien). Un fallo NO reintentable (p. ej. JSON imparseable) no se
reintenta: la saga queda FALLIDA con el paso BLOQUEADO_SOPORTE y también pide
ticket. Los tickets no se abren en línea: un `@Scheduled` (cada 3 h, de 8 a
17) barre las sagas PENDIENTE, abre UN único ticket para todas (escribir un
texto en el log: no hay id de ticket) y las marca ABIERTO con la fecha.
Soporte puede cancelar (solo pre-PASO7, arranca la compensación), reintentar,
marcar-OK manual y consultar/filtrar las sagas — viendo el marcador de ticket
(pendiente/abierto y desde cuándo), la fecha del próximo reintento y si un
paso está bloqueado sin reintento automático. Un planificador purga
periódicamente los datos antiguos que acabaron bien (ServicioLimpiezaDatos).
**Todo termina cuando las sagas se completan: no se publican eventos de
salida.**

No hay sagas "padre" ni "hijas": todas son sagas del mismo concepto (la base
abstracta `Saga<P>`, que NO es un agregado; los agregados son
`SagaPrincipalRoot` y las 3 `SagaSecundariaNRoot`). Que al completarse una
arranquen otras es una decisión del agregado que termina
(`Decision.ArrancarSaga`); la saga arrancada nace independiente, con su
contexto recortado, y no conserva ningún vínculo con la que la originó: la
única correlación es el **`externalId`** (UUID único por tramitación, presente
en las 4 sagas).

En [`docs/`](docs/README.md) hay diagramas de secuencia PlantUML de cada
funcionalidad, con las capas en bloques de izquierda a derecha (adaptadores
de entrada → aplicación → dominio → adaptadores de salida) y líneas de
activación. Se mantienen sincronizados con el código (ver `CLAUDE.md`).

## La idea de la fusión

**Cada Orden = continuar una saga.** La tabla `ordenes` del GestorOrdenes es
la cola de tareas transaccional de las sagas — y es infraestructura pura
(paquete `infraestructure.ordermanager.cola`), no dominio:

| Antes (diseño saga)                   | Ahora (fusionado)                        |
|---------------------------------------|------------------------------------------|
| Scheduler persistente (Quartz/db-sch)  | Orden con `ejecutar_desde` futuro        |
| Caso de uso de recuperación de sagas   | Lease del GestorOrdenes (reentrega sola) |
| Despacho post-commit (hueco de caída)  | Tarea encolada EN la misma transacción   |
| Virtual threads para el paralelismo    | Pool de trabajadores del GestorOrdenes   |
| Timeout de la respuesta programado     | Orden TIMEOUT_SECUNDARIA2 diferida 24 h  |

**Regla de oro:** dentro de la transacción solo BBDD (estado de saga + sagas
nuevas + tareas encoladas); fuera solo I/O externo (REST, tickets). Si
el proceso muere en cualquier punto, el lease reentrega la tarea y
`continuar()` reanuda desde el último paso confirmado.

## Tipos de tarea (contenido JSON de la orden, ver CodecTareaSaga)

- `INICIAR` — crea la saga principal (o la retoma si ya existía: idempotente)
  y ejecuta la cadena síncrona completa dentro de un procesar().
- `ARRANCAR_SAGA` — encolada ×3 en la MISMA transacción que marca la
  principal COMPLETADA y crea las 3 sagas secundarias; lleva el `tipoSaga`
  para rutear al servicio correcto. El pool las procesa en paralelo.
- `REINTENTAR` — con `ejecutar_desde = ahora + backoff`. Sustituye al
  scheduler. El paso viaja por nombre y se reconstruye con `PasoSaga.de`.
- `TIMEOUT_SECUNDARIA2` — encolada al solicitar el paso SOLICITUD, en la misma
  tx; al vencer NO da la respuesta por perdida: consulta el REST de
  conciliación y procesa el resultado si ya existe, o encola otra ventana de
  24 h si no (autocurativo incluso si el proceso murió antes de la llamada REST).
- `RESULTADO_SECUNDARIA2_OK / _ERROR` — las encola el consumer fino de Kafka
  (ConsumidorRespuestaSecundaria2, único uso de Kafka) y hace ack;
  deduplicadas por mensajeId.

## Estructura (business = Java puro + jMolecules; infraestructure = Spring)

```
com/ejemplo/app/
  business/ordermanager/          SOLO JDK + jMolecules (regla en CLAUDE.md,
    │                             verificada por ReglasArquitecturaTest)
    dominio/
      comun/           Shared kernel: Saga<P> (base, no agregado), PasoSaga,
                       Decision<P>, ComandoPaso/ResultadoPaso (marcadoras),
                       ContextoArranque, SagaId, ExternalId, EstadoSaga,
                       EstadoPaso, EstadoTicket, TipoSaga, PoliticaReintentos,
                       RefPaso1/5/7, excepciones.
      sagaprincipal/   SagaPrincipalRoot (@AggregateRoot), PasoSagaPrincipal,
                       ComandoPasoPrincipal, ResultadoPasoPrincipal,
                       ContextoTramitacion, RefPaso2/3/4/6/8, DatoNegocio2/3.
      sagasecundaria1/ SagaSecundaria1Root: INICIO → CONFIRMACION (2 REST).
      sagasecundaria2/ SagaSecundaria2Root: SOLICITUD (REST + evento Kafka).
      sagasecundaria3/ SagaSecundaria3Root: EJECUCION (1 REST).
    aplicacion/
      tarea/           TareaSaga (sealed)
      puerto/entrada/  CasoUso* (iniciar, resultados<P>, intervenir,
                       consultar, limpiar, abrir tickets pendientes,
                       registrar respuesta secundaria 2)
      puerto/salida/   PuertoPaso1..8, PuertoSagaSecundaria1/2/3,
                       PuertoConciliacionSecundaria2,
                       RepositorioSagaPrincipal + RepositorioSagaSecundaria1/2/3,
                       PuertoColaTareas, PuertoMensajesProcesados,
                       PuertoTicketsSoporte, PuertoSagasTicketPendiente,
                       PuertoConsultaSagasSoporte, UnidadDeTrabajo
      servicio/        ServicioSagaBase<P,S> + ServicioSagaPrincipal +
                       ServicioSagaSecundaria1/2/3, ManejadorTareasSaga,
                       ServicioSoporteSagas, ServicioEncolarTramitacion,
                       ServicioRegistrarRespuestaSecundaria2,
                       ServicioTicketsSoporte, ServicioLimpiezaDatos
  infraestructure/ordermanager/   Spring, JPA, Kafka, Jackson
    cola/    El motor genérico de trabajo: Orden (@Entity), EstadoOrden,
             ProcesadorOrden, GestorOrdenes, TrabajadorOrdenes,
             ServicioOrdenes, RepositorioOrdenes, ConfiguracionEjecucionAsincrona.
             No importa NADA del modelo de sagas.
    saga/    El puente sagas<->gestor: ProcesadorOrdenSaga, AdaptadorColaTareas,
             CodecTareaSaga, UnidadDeTrabajoSpring, PlanificadorLimpieza,
             PlanificadorTicketsSoporte, AdaptadorTicketsLog,
             ConsumidorRespuestaSecundaria2, ConfiguracionAplicacion.
  OrderManagerApplication.java
resources/db/migration/V1, V2       resources/application.yml
```

Build: Gradle (`./gradlew build`). El test `ReglasArquitecturaTest` (ArchUnit)
verifica que `business/**` no depende de Spring/JPA/Jackson/Kafka ni de
`infraestructure/**`.

## Ajustes del GestorOrdenes para la fusión (mínimos)

1. `ordenes` gana `ejecutar_desde` (tareas diferidas), `saga_id` y `tipo_tarea`
   (migración V2); candidatas/reclamo filtran por `ejecutar_desde <= now`.
2. `ServicioOrdenes.encolar(...)` con propagation REQUIRED: se une a la
   transacción de los servicios de saga (UnidadDeTrabajoSpring).
3. `lease-segundos` sube a 300: la tarea INICIAR ejecuta la cadena de 8 pasos
   dentro de un procesar(); el lease debe superar su peor caso o otro
   trabajador robará la orden a mitad. Dimensiónalo con la suma de timeouts HTTP.
4. Una orden FALLIDA ya no significa "fallo de negocio" (eso lo absorbe el
   dominio con reintentos/tickets): significa bug o infraestructura rota.
   Pon una alerta sobre FALLIDA.

## Idempotencia (contrato de ProcesadorOrden, ahora crítico)

La reentrega por lease implica at-least-once hacia los servicios externos:
si un paso quedó SOLICITADO sin resultado registrado, `continuar()` lo
re-ejecuta. Los servicios de los pasos 1, 3 y 8 y de las 3 sagas secundarias
deben ser idempotentes por externalId (o tolerar el duplicado). Dentro del
sistema, la idempotencia la garantizan los guards de estado de los agregados
y la dedup por mensajeId.

## Pendiente de implementar (infra restante)

- Adaptadores REST de PuertoPaso1..8, PuertoSagaSecundaria1/2/3 y
  PuertoConciliacionSecundaria2 (fallo → ExcepcionServicioExterno).
- Repositorios JPA de las 4 sagas con versión optimista
  (lanzar ConcurrenciaOptimistaException) + sus migraciones (incluir
  estado_ticket y ticket_abierto_en).
- PuertoMensajesProcesados (tabla dedup, misma tx).
- PuertoConsultaSagasSoporte (queries para la pantalla: sagasBloqueadas,
  sagasEnEjecucion, sagasConTicket, buscar con FiltroSagas, vistaTramitacion
  por externalId; une ordenes por saga_id para proximoReintentoEn y las tareas
  pendientes/diferidas).
- PuertoSagasTicketPendiente (query de sagas con estado_ticket = PENDIENTE).
  PuertoTicketsSoporte ya está implementado (AdaptadorTicketsLog: el ticket
  es un texto en el log).
- Controlador REST del backoffice sobre CasoUsoIntervenirSaga (mapear
  PuntoNoRetornoSuperadoException → 409; el paso viaja por nombre).
