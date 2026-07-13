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
| [05-saga-secundaria2](05-saga-secundaria2.png) | Saga secundaria 2: solicitud REST, respuesta diferida por evento Kafka (puede tardar), timeout vigilante de 24 h |
| [06-saga-secundaria3](06-saga-secundaria3.png) | Saga secundaria 3: una única llamada REST y COMPLETADA |
| [07-error-ticket-soporte](07-error-ticket-soporte.png) | Fallo de un paso: backoff exponencial y, al agotarse o no ser reintentable, ticket a soporte |
| [08-operaciones-soporte](08-operaciones-soporte.png) | Soporte: consultar/filtrar sagas y cancelar la principal con compensación |
| [09-limpieza-datos](09-limpieza-datos.png) | Purga periódica de sagas finalizadas-bien, dedup caducado y tareas terminadas |

## Diagramas de estado y de clases

| Diagrama | Qué muestra |
|---|---|
| [13-maquinas-de-estado](13-maquinas-de-estado.png) | Máquinas de `EstadoSaga` y `EstadoPaso` (negocio) y `EstadoOrden` (cola de infraestructura) |
| [14-clases-dominio](14-clases-dominio.png) | Dominio: base `Saga<P>`, los 4 `@AggregateRoot`, enums de paso por saga, `Decision<P>`, `ExternalId` |
| [15-clases-aplicacion](15-clases-aplicacion.png) | Aplicación: `ServicioSagaBase` + 4 servicios, casos de uso y puertos de salida |
| [16-clases-infraestructura](16-clases-infraestructura.png) | Infraestructura: la cola de órdenes con lease (`cola`) y los adaptadores de saga (`saga`) |

## Regenerar los PNG

```bash
java -jar ~/.claude/tools/plantuml.jar -tpng -charset UTF-8 docs/*.puml
```

(O usar la skill `puml-to-png`.)
