# Diagramas (PlantUML)

Los diagramas se escriben en `.puml` y se convierten a PNG; **deben
actualizarse a la vez que el código** (ver `CLAUDE.md` en la raíz del repo).

Los diagramas de secuencia separan las capas en cuatro bloques, en este
orden de izquierda a derecha para que las flechas fluyan de forma natural:

- **Adaptadores de entrada** (azul): API REST, `GestorOrdenes`/`TrabajadorOrdenes`
  (sondeo + pool + lease sobre la tabla `ordenes`), consumer de Kafka y
  planificadores `@Scheduled`.
- **Aplicación** (verde): servicios de orquestación y casos de uso. Regla de
  oro: dentro de la transacción solo BBDD; fuera de ella solo I/O externo.
- **Dominio** (naranja): los agregados `Saga*`. Deciden (`Decision`) y nunca
  hacen I/O.
- **Adaptadores de salida** (violeta, siempre a la derecha): repositorios,
  tabla `ordenes`, clientes REST de los pasos, Kafka, tickets y eventos.

Todos muestran las líneas de activación para poder seguir el flujo.

## Diagramas de secuencia

| Diagrama | Funcionalidad |
|---|---|
| [01-arranque-saga-nueva](01-arranque-saga-nueva.puml) | Arranque de una nueva saga: el pool encuentra en BBDD una orden en estado PENDIENTE, la reclama (lease) y crea la `SagaPrincipal` |
| [02-pasos-saga-principal](02-pasos-saga-principal.puml) | Todos los pasos de la saga principal (PASO1 → … → PASO8), con checkpoint transaccional por paso |
| [03-finalizacion-saga-principal](03-finalizacion-saga-principal.puml) | Finalización de la principal: COMPLETADA + 3 sagas sucesoras + 3 órdenes PENDIENTE en un solo commit |
| [04-saga-asincrona](04-saga-asincrona.puml) | Saga ASINCRONA de principio a fin: comando por Kafka, timeout vigilante, respuesta por evento con dedup |
| [05-saga-secuencial](05-saga-secuencial.puml) | Saga SECUENCIAL de principio a fin: SECUENCIAL1 → SECUENCIAL2 |
| [06-saga-simple](06-saga-simple.puml) | Saga SIMPLE de principio a fin: un único paso síncrono |
| [07-error-ticket-soporte](07-error-ticket-soporte.puml) | Error en un paso: backoff exponencial y, al agotarse o no ser reintentable, ticket al equipo de soporte |
| [08-operaciones-soporte](08-operaciones-soporte.puml) | Operaciones de soporte: consultar sagas en ejecución, filtrar por estado/fecha de inicio/última actualización y arrancar la saga compensatoria |
| [09-limpieza-datos](09-limpieza-datos.puml) | Limpieza periódica: purga de sagas antiguas que acabaron bien, dedup caducado y tareas terminadas |

## Diagramas de estados y de clases

| Diagrama | Contenido |
|---|---|
| [13-maquinas-de-estado](13-maquinas-de-estado.puml) | Máquinas de estado de `EstadoSaga` y `EstadoPaso` |
| [14-clases-dominio](14-clases-dominio.puml) | Clases del dominio |
| [15-clases-aplicacion](15-clases-aplicacion.puml) | Casos de uso, servicios y puertos de la aplicación |
| [16-clases-infraestructura](16-clases-infraestructura.puml) | Gestor de órdenes (lease) y adaptadores |

## Cómo renderizar

Con la skill `puml-to-png` del repo, o a mano:

```bash
java -jar ~/.claude/tools/plantuml.jar -tpng -charset UTF-8 docs/*.puml
```
