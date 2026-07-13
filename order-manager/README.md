# Sagas + GestorOrdenes fusionados (Java 21)

Flujo: saga principal `PASO1 → PASO2 → ... → PASO8` y, al completar el PASO8,
arrancan 3 sagas independientes sin join: `ASINCRONA` (un paso vía Kafka),
`SECUENCIAL` (SECUENCIAL1→SECUENCIAL2) y `SIMPLE` (un paso síncrono).
Punto de no retorno en PASO7; backoff 30s→4h, ticket al 10º intento;
soporte puede cancelar (solo pre-PASO7, arranca la compensación), reintentar,
marcar-OK manual y consultar/filtrar las sagas (estado, fecha de inicio,
fecha de última actualización). Un planificador purga periódicamente los
datos antiguos que acabaron bien (ServicioLimpiezaDatos).

No hay sagas "padre" ni "hijas": todas son sagas del mismo concepto (`Saga`).
Que al completarse una arranquen otras es una decisión del agregado que
termina (`Decision.ArrancarSaga`); la saga arrancada nace independiente, con
su contexto recortado, y no conserva ningún vínculo con la que la originó
(la correlación de negocio es el `datoNegocio1Id`).

En [`docs/`](docs/README.md) hay diagramas de secuencia PlantUML de cada
funcionalidad, con las capas en bloques de izquierda a derecha (adaptadores
de entrada → aplicación → dominio → adaptadores de salida) y líneas de
activación. Se mantienen sincronizados con el código (ver `CLAUDE.md`).

## La idea de la fusión

**Cada Orden = continuar una saga.** La tabla `ordenes` del GestorOrdenes pasa
a ser la cola de tareas transaccional de las sagas:

| Antes (diseño saga)                   | Ahora (fusionado)                        |
|---------------------------------------|------------------------------------------|
| Scheduler persistente (Quartz/db-sch)  | Orden con `ejecutar_desde` futuro        |
| Caso de uso de recuperación de sagas   | Lease del GestorOrdenes (reentrega sola) |
| Despacho post-commit (hueco de caída)  | Tarea encolada EN la misma transacción   |
| Virtual threads para el paralelismo    | Pool de trabajadores del GestorOrdenes   |
| Timeout del paso asíncrono programado  | Orden TIMEOUT_ASINCRONO diferida 15 min  |

**Regla de oro:** dentro de la transacción solo BBDD (estado de saga + sagas
nuevas + tareas encoladas); fuera solo I/O externo (REST, Kafka, tickets). Si
el proceso muere en cualquier punto, el lease reentrega la tarea y
`continuar()` reanuda desde el último paso confirmado.

## Tipos de tarea (contenido JSON de la orden, ver CodecTareaSaga)

- `INICIAR` — crea la saga principal (o la retoma si ya existía: idempotente)
  y ejecuta la cadena síncrona completa dentro de un procesar().
- `ARRANCAR_SAGA` — encolada ×3 en la MISMA transacción que marca la
  principal COMPLETADA y crea las 3 sagas nuevas. El pool las procesa en paralelo.
- `REINTENTAR` — con `ejecutar_desde = ahora + backoff`. Sustituye al scheduler.
- `TIMEOUT_ASINCRONO` — encolada al solicitar el paso ASINCRONO, en la misma
  tx; si la respuesta no llega en 15 min se convierte en fallo reintentable
  (autocurativo incluso si el proceso murió antes de publicar en Kafka).
- `RESULTADO_ASINCRONO_OK / _ERROR` — las encola el consumer fino de Kafka
  (ConsumidorRespuestaAsincrona) y hace ack; deduplicadas por mensajeId.

## Estructura

Nada vive suelto en la raíz del demonio: cada capa separa en dos subcarpetas
la saga (el negocio) del gestor de órdenes (el motor de trabajo).

```
com/ejemplo/tramitacion/
  dominio/
    saga/    NÚCLEO puro de las sagas (sin frameworks; junto con
             aplicacion/saga compila con javac 21): Saga, SagaPrincipal,
             SagaSucesora (Asincrona/Secuencial/Simple), Decision,
             ComandoPaso, ResultadoPaso, value objects y excepciones.
    orden/   Modelo del gestor de trabajo: Orden, EstadoOrden,
             ProcesadorOrden (contrato de procesamiento, idempotente).
             La entidad lleva anotaciones JPA a propósito: el patrón lease
             se apoya en la tabla y un mapeo aparte sería 1:1.
  aplicacion/
    saga/
      tarea/            TareaSaga (sealed)
      puerto/entrada/   CasoUso* (iniciar, intervenir, consultar, resultados)
      puerto/salida/    PuertoPaso1..8, PuertoAsincrono/Secuencial/Simple,
                        PuertoColaTareas, repositorios, UnidadDeTrabajo...
      servicio/         ServicioSagaPrincipal, ServicioSagasSucesoras,
                        ManejadorTareasSaga, ServicioEncolarTramitacion,
                        ServicioSoporteSagas
  infraestructura/
    orden/   El demonio genérico (Spring): GestorOrdenes, TrabajadorOrdenes,
             ServicioOrdenes, RepositorioOrdenes, ConfiguracionEjecucionAsincrona.
             No importa NADA del paquete de sagas.
    saga/    El puente sagas<->gestor: ProcesadorOrdenSaga, AdaptadorColaTareas,
             CodecTareaSaga, UnidadDeTrabajoSpring, ConsumidorRespuestaAsincrona.
resources/db/migration/V1, V2       resources/application.yml
```

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
re-ejecuta. Los servicios de los pasos 1, 3 y 8 y de las sagas ASINCRONA,
SECUENCIAL y SIMPLE deben ser idempotentes por datoNegocio1 (o tolerar el
duplicado). Dentro del sistema, la idempotencia la garantizan los guards de
estado de los agregados y la dedup por mensajeId.

## Pendiente de implementar (infra restante)

- Adaptadores REST de PuertoPaso1..PuertoSimple (fallo → ExcepcionServicioExterno).
- PuertoAsincrono: publicación del comando en Kafka con sagaId como correlación.
- Repositorios JPA de SagaPrincipal/SagaSucesora con versión optimista
  (lanzar ConcurrenciaOptimistaException) + sus migraciones.
- PuertoMensajesProcesados (tabla dedup, misma tx).
- PuertoConsultaSagasSoporte (queries para la pantalla: sagasBloqueadas,
  sagasEnEjecucion, buscar con FiltroSagas; puede unir ordenes por saga_id
  para mostrar también las tareas pendientes/diferidas).
- Adaptadores JPA de las purgas de la limpieza (purgarFinalizadasAntesDe de
  ambos repositorios y purgarAnterioresA del dedup; la de ordenes ya existe).
- Controlador REST del backoffice sobre CasoUsoIntervenirSaga (mapear
  PuntoNoRetornoSuperadoException → 409).
