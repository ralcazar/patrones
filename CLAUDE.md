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

## order-manager: pureza de las capas business

- Todo lo que cuelga de `order-manager/src/main/java/com/ejemplo/app/business/**`
  (dominio + aplicación) se escribe SOLO con Java puro (JDK) y la librería
  **jMolecules** (`org.jmolecules:jmolecules-ddd`), cuyas anotaciones
  (`@AggregateRoot`, `@Entity`, `@ValueObject`, `@Repository`, `@Service`,
  `@Factory`, `@Identity` de `org.jmolecules.ddd.annotation.*`) expresan los
  conceptos DDD del modelo.
- Prohibido en `business/**`: Spring (`org.springframework.*`), JPA
  (`jakarta.persistence.*`), Jackson (`com.fasterxml.*`), Kafka y cualquier
  otra librería de infraestructura. Todo eso vive exclusivamente bajo
  `com.ejemplo.app.infraestructure/**`.
- La regla la verifica el test de ArchUnit `ReglasArquitecturaTest`
  (`order-manager/src/test/java/...`): si un cambio lo rompe, el cambio está
  mal ubicado, no el test.
