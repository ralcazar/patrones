# Diagramas de order-manager

Diagramas PlantUML del gestor de órdenes fusionado con las sagas. Los `.puml`
y sus `.png` se versionan juntos y se actualizan en el mismo cambio que el
código que documentan (ver `CLAUDE.md` en la raíz del repo).

## Convención de los diagramas de secuencia

Cada diagrama separa las capas en bloques (`box`), de izquierda a derecha:

| Bloque | Color | Contenido |
|---|---|---|
| Adaptadores de entrada | azul `#EFF5FB` | REST, pool del gestor, consumer Kafka |
| Aplicación | verde `#F5FBEF` | casos de uso y servicios de saga |
| Dominio | naranja `#FBF5EF` | agregados (`SagaPrincipalRoot`, `SagaSecundariaNRoot`) |
| Adaptadores de salida | violeta `#F3EFFB` | repositorios, tabla `ordenes`, puertos REST/Kafka |

Regla de oro en todos los flujos: **dentro de la transacción solo BBDD**
(estado de la saga + tareas encoladas); **fuera de ella solo I/O externo**
(REST, tickets). Las flechas fluyen de izquierda a derecha y se muestran las
líneas de activación.

## Diagramas de secuencia

| Diagrama | Qué muestra |
|---|---|
| [01-arranque-saga-nueva](01-arranque-saga-nueva.png) | Intake durable y arranque: el pool encuentra la orden `PENDIENTE` y crea la `SagaPrincipalRoot` |
| [02-pasos-saga-principal](02-pasos-saga-principal.png) | La cadena PASO1→…→PASO8 con checkpoint transaccional por paso |
| [03-finalizacion-saga-principal](03-finalizacion-saga-principal.png) | COMPLETADA + creación de las 3 sagas secundarias + 3 órdenes `ARRANCAR_SAGA` en un solo commit (sin eventos) |
| [04-saga-secundaria1](04-saga-secundaria1.png) | Saga secundaria 1: INICIO → CONFIRMACION, dos llamadas REST a métodos distintos del mismo servicio |
| [05-saga-secundaria2](05-saga-secundaria2.png) | Saga secundaria 2: solicitud REST, respuesta diferida por evento Kafka (puede tardar) y timeout de 24 h con conciliación REST (si no hay resultado, otra ventana de 24 h) |
| [06-saga-secundaria3](06-saga-secundaria3.png) | Saga secundaria 3: una única llamada REST y COMPLETADA |
| [07-error-ticket-soporte](07-error-ticket-soporte.png) | Fallo de un paso: backoff 1..180 min con reintento indefinido (o saga FALLIDA si no es reintentable), flag "abrir ticket pendiente" y barrido `@Scheduled` que abre UN ticket para todas |
| [08-operaciones-soporte](08-operaciones-soporte.png) | Soporte: consultar sagas (ticket pendiente/abierto, próximo reintento, pasos bloqueados) y cancelar la principal con compensación |
| [09-limpieza-datos](09-limpieza-datos.png) | Purga periódica de sagas finalizadas-bien, dedup caducado y tareas terminadas |

## Diagramas de estado y de clases

Los diagramas de clases están troceados por subconjunto (capa + saga) para
que se lean sin cruces de líneas: uno por capa de dominio/aplicación de cada
saga, más el núcleo común, soporte e infraestructura.

| Diagrama | Qué muestra |
|---|---|
| [13-maquinas-de-estado](13-maquinas-de-estado.png) | Máquinas de `EstadoSaga`, `EstadoPaso` y `EstadoTicket` (negocio) y `EstadoOrden` (cola de infraestructura) |
| [14-clases-dominio-comun](14-clases-dominio-comun.png) | Dominio, shared kernel: base `Saga<P>`, `EjecucionPaso`, `Decision<P>`, `PoliticaReintentos` (1..180 min), enums y VOs |
| [15-clases-dominio-saga-principal](15-clases-dominio-saga-principal.png) | Dominio de la saga principal: `SagaPrincipalRoot`, pasos, comandos/resultados, `ContextoTramitacion` |
| [16-clases-dominio-sagas-secundarias](16-clases-dominio-sagas-secundarias.png) | Dominio de las 3 sagas secundarias: agregados, pasos y comandos/resultados |
| [17-clases-aplicacion-nucleo](17-clases-aplicacion-nucleo.png) | Aplicación, núcleo: `ServicioSagaBase`, `ManejadorTareasSaga`, `TareaSaga`, intake y puertos transversales |
| [18-clases-aplicacion-saga-principal](18-clases-aplicacion-saga-principal.png) | Aplicación de la saga principal: `ServicioSagaPrincipal`, repos y `PuertoPaso1..8` |
| [19-clases-aplicacion-saga-secundaria1](19-clases-aplicacion-saga-secundaria1.png) | Aplicación de la saga secundaria 1: servicio, repo y puerto REST (dos métodos) |
| [20-clases-aplicacion-saga-secundaria2](20-clases-aplicacion-saga-secundaria2.png) | Aplicación de la saga secundaria 2: timeout 24 h, `PuertoConciliacionSecundaria2` y el caso de uso del consumer Kafka |
| [21-clases-aplicacion-saga-secundaria3](21-clases-aplicacion-saga-secundaria3.png) | Aplicación de la saga secundaria 3: servicio, repo y puerto REST |
| [22-clases-aplicacion-soporte](22-clases-aplicacion-soporte.png) | Aplicación de soporte: intervenciones/consultas, `ServicioTicketsSoporte` (ticket único vía log) y limpieza |
| [23-clases-infraestructura-cola](23-clases-infraestructura-cola.png) | Infraestructura: la cola de órdenes con lease (paquete `cola`) |
| [24-clases-infraestructura-saga](24-clases-infraestructura-saga.png) | Infraestructura: el puente sagas↔gestor (codec, adaptadores, consumer, planificadores de limpieza y tickets) |

## Regenerar los PNG

```bash
java -jar ~/.claude/tools/plantuml.jar -tpng -charset UTF-8 docs/*.puml
```

(O usar la skill `puml-to-png`.)
