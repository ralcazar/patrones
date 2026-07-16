# CLAUDE.md

Guía para trabajar en este repositorio.

## Diagramas: siempre sincronizados con el código

- Los diagramas viven en `order-manager/docs/` y son parte del entregable:
  **cualquier cambio de código que afecte al flujo, a las capas o a los
  contratos documentados debe actualizar los diagramas en el mismo cambio**.
  Nunca dejar diagramas desactualizados respecto al código.
- Los diagramas se escriben en PlantUML (`.puml`) y **después se convierten a
  PNG** (usar la skill `puml-to-png`: `plantuml.jar` con `-tpng -charset UTF-8`).
  Los `.puml` y sus `.png` se actualizan y versionan juntos.
- Diagramas de secuencia: separar las capas en bloques (`box`) en este orden,
  de izquierda a derecha: **adaptadores de entrada → aplicación → dominio →
  adaptadores de salida**. La infraestructura de los adaptadores de salida va
  siempre a la derecha, de forma que las flechas fluyan de izquierda a derecha
  en un flujo natural. Mostrar líneas de activación (`activate`/`deactivate`)
  para poder seguir el flujo.
- Mantener también al día el índice `order-manager/docs/README.md`.
- Las llamadas que aparecen en un diagrama de secuencia deben corresponder a
  llamadas que existen realmente en el código (mismo método, misma clase/
  interfaz). No se inventan ni se resumen pasos que el código no tiene.
- En cada llamada se muestran solo los parámetros significativos para explicar
  el flujo (los que cambian el comportamiento o el estado relevante), no la
  firma completa si tiene detalles irrelevantes para el lector del diagrama.
- No comprimir el flujo: el diagrama debe quedar muy cercano al código real
  (mismo número de pasos, mismas transacciones, mismas condiciones), no una
  versión simplificada o idealizada de lo que hace el código.

## Restricción de arquitectura: entrada → aplicación → salida

- **Un adaptador de entrada nunca habla directamente con un adaptador de
  salida** (ni con sus puertos de salida). Debe pasar como mínimo por la capa
  de aplicación (un caso de uso / servicio de aplicación) y, si la operación
  lo requiere, por la capa de dominio.
- Ejemplo: el consumer de Kafka (`ConsumidorRespuestaSecundaria2`, adaptador
  de entrada) no encola en `PuertoColaTareas` directamente; invoca el caso de
  uso `CasoUsoRegistrarRespuestaSecundaria2` y es su servicio de aplicación
  quien encola. Lo mismo aplica a los `@Scheduled` (invocan casos de uso) y a
  cualquier adaptador REST futuro.

## order-manager: pureza de las capas business

- Todo lo que cuelga de `order-manager/src/main/java/com/ejemplo/app/business/**`
  (dominio + aplicación) se escribe SOLO con Java puro (JDK) y la librería
  **jMolecules** (`org.jmolecules:jmolecules-ddd`), cuyas anotaciones
  (`@AggregateRoot`, `@Entity`, `@ValueObject`, `@Repository`, `@Service`,
  `@Factory`, `@Identity` de `org.jmolecules.ddd.annotation.*`) expresan los
  conceptos DDD del modelo.
- Única excepción: `jakarta.transaction.Transactional` está permitido en
  `aplicacion/**` para marcar la frontera transaccional (es un estándar
  Jakarta, no un framework; Spring lo reconoce igual que su propio
  `@Transactional` sin que la capa business dependa de Spring). Los servicios
  de aplicación con REST fuera de transacción (los `ServicioSaga*`,
  `ServicioContinuarSaga`, `ServicioTicketsSoporte`) se inyectan a sí mismos
  (`self`, el proxy transaccional) para invocar su parte `@Transactional` sin
  que la auto-invocación la ignore; ver `ConfiguracionAplicacion` (el `@Lazy`
  vive ahí, en infraestructure, no en business).
- Prohibido en `business/**`: Spring (`org.springframework.*`), JPA
  (`jakarta.persistence.*`), Jackson (`com.fasterxml.*`), Kafka y cualquier
  otra librería de infraestructura. Todo eso vive exclusivamente bajo
  `com.ejemplo.app.infraestructure/**`.
- La regla la verifica el test de ArchUnit `ReglasArquitecturaTest`
  (`order-manager/src/test/java/...`): si un cambio lo rompe, el cambio está
  mal ubicado, no el test.
