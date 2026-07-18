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
  de entrada, en `infraestructure.sagas.eventos`) no toca `RepositorioOrden`
  ni el agregado directamente; invoca el caso de uso
  `CasoUsoRegistrarRespuestaSecundaria2` (`business.sagas`) y es su servicio
  de aplicación (`ServicioRegistrarRespuestaSecundaria2`) quien muta y
  guarda. Lo mismo aplica a los `@Scheduled` (invocan casos de uso) y a
  cualquier adaptador REST futuro.

## Restricción de arquitectura: ordermanager ↛ sagas

- El motor de órdenes (`business.ordermanager` + `infraestructure.ordermanager`)
  es genérico en el tipo de orden: **ninguna clase de `ordermanager` puede
  depender de `business.sagas` ni de `infraestructure.sagas`**. La
  dependencia va siempre `sagas -> ordermanager`, nunca al revés — así el
  motor se puede llevar a otra aplicación y definir allí otros tipos de orden
  sin tocar su código.
- El motor tampoco puede nombrar el concepto "saga" en sus propias clases
  (vocabulario neutro): usa `OrdenId`, `Proceso<E>`, `TipoOrden` (VO abierto,
  no un enum cerrado), `ProcesadorOrden`, nunca `SagaId`/`Saga`/`TipoSaga`/
  `ServicioSaga`.
- El motor expone 3 puntos de extensión (SPI) para que las sagas (o
  cualquier otro tipo de orden futuro) se registren sin que el motor las
  conozca: `ProcesadorOrden` (ejecuta un paso, en
  `business.ordermanager.aplicacion.servicio`), `MapeadorProceso` y
  `DescriptorSoporteOrden` (persistencia y modelo de lectura por tipo, ambas
  en `infraestructure.ordermanager.persistencia`). Las implementaciones
  concretas de las 4 sagas (`ServicioSagaPrincipal`/`Secundaria1/2/3`,
  `SoporteSagaPrincipal`/`Secundaria1/2/3`) viven en `business.sagas` /
  `infraestructure.sagas` y se registran como `List<...>` que Spring inyecta
  y el motor indexa por `tipo()`.
- La regla la verifica el test de ArchUnit `ReglasArquitecturaTest`
  (`ordermanagerNoDependeDeSagas`, sobre producción y tests, y
  `ordermanagerSinVocabularioDeSagas`): si un cambio lo rompe, el cambio está
  mal ubicado, no el test. Ver `order-manager/docs/README.md` y los
  diagramas 14, 17, 23 y 24-25 para el detalle de las 3 SPI y la frontera.

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
  de aplicación con REST fuera de transacción (los `ServicioSaga*` de
  `business.sagas`, `ServicioContinuarOrden`, `ServicioTicketsSoporte`, estos
  dos últimos de `business.ordermanager`) se inyectan a sí mismos (`self`, el
  proxy transaccional) para invocar su parte `@Transactional` sin que la
  auto-invocación la ignore; ver `ConfiguracionOrderManager`
  (`infraestructure.ordermanager`) y `ConfiguracionSagas`
  (`infraestructure.sagas`) — el `@Lazy` vive ahí, en infraestructure, no en
  business.
- Prohibido en `business/**`: Spring (`org.springframework.*`), JPA
  (`jakarta.persistence.*`), Jackson (`com.fasterxml.*`), Kafka y cualquier
  otra librería de infraestructura. Todo eso vive exclusivamente bajo
  `com.ejemplo.app.infraestructure/**`.
- La regla la verifica el test de ArchUnit `ReglasArquitecturaTest`
  (`order-manager/src/test/java/...`): si un cambio lo rompe, el cambio está
  mal ubicado, no el test.

## Cobertura de tests: 100% + separación unit/integración

- **100% de instrucción y rama**, verificado por
  `jacocoTestCoverageVerification` con `minimum = 1.0` en `INSTRUCTION` y
  `BRANCH` (agregando `test` + `integrationTest`), enganchado a `check`. Todo
  cambio de código entra con sus tests en el mismo commit y mantiene el 100%;
  nunca se baja el umbral para "aprobar" un commit.
- **Exclusiones**: solo `OrderManagerApplication` (clase de arranque de
  Spring Boot, sin lógica propia) + los miembros generados que JaCoCo filtra
  por defecto. Cualquier exclusión nueva exige una línea de justificación
  aquí y en `build.gradle` (el conjunto `jacocoExclusions`); nunca se baja el
  umbral para evitar cubrir un hueco real.
- **Dos source sets**: `src/test` = tests **unitarios** (Java puro + dobles
  en memoria, SIN Spring, task `test`); `src/integrationTest` = tests de
  **integración** (`@SpringBootTest` / H2, task `integrationTest`, extiende
  las dependencias de `test` vía `integrationTestImplementation.extendsFrom
  testImplementation` y reutiliza `sourceSets.main.output` +
  `sourceSets.test.output` para los dobles de `testsoporte/`). Dentro de cada
  uno, la misma estructura que producción: por contexto
  (`ordermanager`/`sagas`) y por capa (`business`/`infraestructure`).
- **Infra en los tests**: prohibido Docker, Testcontainers, `@EmbeddedKafka`
  o cualquier broker/servicio externo. La ÚNICA infra que se levanta es
  **H2 en memoria, modo Oracle** (`jdbc:h2:mem:...;MODE=Oracle`), y solo en
  `src/integrationTest` para probar los adaptadores JPA. Kafka se prueba sin
  broker: el consumer (`ConsumidorRespuestaSecundaria2`) es un test unitario
  que invoca `onRespuesta(...)` directamente. Los puertos de salida aún sin
  implementación se sustituyen por dobles en el contexto Spring de test.
- **Concurrencia**: la concurrencia optimista se prueba de forma
  determinista (repos-decoradores que fuerzan el conflicto) en los tests
  unitarios, más un test de integración con hilos reales (`ExecutorService`
  + `CountDownLatch`) sobre H2 para la carrera de verdad.
- **Tasks**: `./gradlew test` (unitarios), `./gradlew integrationTest`
  (integración), `./gradlew jacocoTestReport` (informe agregando ambos
  source sets), `./gradlew check` (test + integrationTest +
  jacocoTestCoverageVerification + ArchUnit).
- **Endurecimiento**: `ReglasArquitecturaTest.srcTestNoDependeDeSpring`
  verifica (por la ruta real del `.class` compilado, no por convención de
  nombre) que ningún test de `src/test` dependa de `org.springframework..`;
  si alguno lo necesita de verdad, va a `src/integrationTest`.
