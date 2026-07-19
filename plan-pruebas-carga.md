# Plan: pruebas de carga multi-pod del order-manager

Plan de implementación pensado para ejecutarse de forma autónoma por
subagentes implementadores, orquestados por un agente principal. Cada fase
es autocontenida, termina con verificación por comando y un commit propio.

## Objetivo

Poder simular N pods ejecutando el motor de órdenes en paralelo contra una
misma base de datos, con mocks de las llamadas REST que inyectan latencia y
una tasa de fallo configurable, y analizar después (log estructurado + BBDD)
si el sistema se comporta bien: throughput, colisiones optimistas, ritmo de
reclamo, reintentos, tickets, órdenes estancadas.

**Qué mide y qué no**: detecta cuellos de botella *algorítmicos* (lote,
intervalo, workers, política de reintentos, contención optimista). NO da
cifras extrapolables a producción (H2 embebida no es Oracle, N pods
comparten una máquina). Las conclusiones válidas son relativas entre
escenarios.

## Estado de partida (ya hecho, no rehacer)

- Los ficheros de escenario existen en
  `order-manager/src/pruebaCarga/resources/escenarios/` (`README.md` con el
  esquema + 7 escenarios: `humo`, `base-sin-fallos`, `fallos-01/10/30`,
  `contencion-8-pods`, `respuestas-perdidas`). **Son la fuente de verdad**:
  el lanzador no acepta parámetros sueltos, solo `-Pescenario=<nombre>`.
  Se puede *extender* su esquema (y el README, y los yml, todo coherente)
  si falta un knob necesario; no se cambia su intención ni sus valores de
  comparación (la familia `base-sin-fallos`/`fallos-*` solo difiere en
  `tasa-fallo`).

## Reglas innegociables (resumen de CLAUDE.md; leerlo entero antes de empezar)

1. **Cobertura 100%** (instrucción y rama, `./gradlew check`): todo código
   nuevo en `src/main` entra con sus tests en el mismo commit. Nunca se baja
   el umbral ni se añaden exclusiones JaCoCo sin justificación escrita.
   El código de `src/pruebaCarga` NO computa (no lo ejecutan `test` ni
   `integrationTest`), no necesita exclusión.
2. **Pureza business**: `business/**` = Java puro + jMolecules (+
   `jakarta.transaction.Transactional` en aplicación). Nada de SLF4J,
   Spring, etc. ahí. Todo logging vive en `infraestructure/**` o en
   `src/pruebaCarga`.
3. **ordermanager ↛ sagas** y vocabulario neutro en el motor (nada de
   "saga" en clases de `ordermanager`). Lo vigila `ReglasArquitecturaTest`.
4. **Diagramas sincronizados**: si una fase cambia contratos de PROD
   (fase 0 lo hace: puerto nuevo), se actualizan los `.puml` afectados, se
   regeneran los `.png` (skill `puml-to-png`: `plantuml.jar -tpng -charset
   UTF-8`) y se actualiza `order-manager/docs/README.md`, todo en el mismo
   commit.
5. **Nada de Docker, Testcontainers ni brokers**. La única infra es H2
   (en memoria para integrationTest; en fichero para pruebaCarga).
6. **Nomenclatura en español** en todo el código nuevo, sin mezclar idiomas.
7. Prohibido `ON DELETE CASCADE` y borrados implícitos (no aplica salvo que
   una fase toque el esquema; no debería).

## Decisiones de diseño ya tomadas (no reabrirlas)

- **N pods = N contextos Spring en la misma JVM**, cada uno con sus
  planificadores y su pool, todos contra el mismo
  `jdbc:h2:file:...;MODE=Oracle`. Sin procesos separados, sin `AUTO_SERVER`.
- **Sin servidor web** en los pods (`spring.main.web-application-type=none`).
  La inyección de tramitaciones invoca el mismo caso de uso que usa
  `ControladorTramitaciones` (descubrirlo en el código), no HTTP.
- **Sin Kafka**: en el perfil de carga se excluye la autoconfiguración de
  Kafka y el bean `ConsumidorRespuestaSecundaria2`; un simulador invoca
  `CasoUsoRegistrarRespuestaSecundaria2` directamente tras un retraso.
- **Mocks solo de puertos externos** (REST y equivalentes):
  `PuertoPaso1..8`, `PuertoSagaSecundaria1/2/3` y los demás puertos que en
  producción serían llamadas a otros sistemas. La persistencia usa los
  adaptadores JPA reales contra la H2 en fichero.
- **Los mocks duermen** (latencia uniforme min–max) además de fallar con la
  probabilidad configurada. Sin latencia el perfil de concurrencia sería
  ficticio. Aleatoriedad con `Random(semilla + índicePod)` por pod:
  reproducible en distribución (el entrelazado de hilos no es determinista;
  no intentar arreglarlo).
- **Log estructurado de una línea** `clave=valor` (ver catálogo de eventos
  en fase 0), pensado para agregarse con un analizador determinista; el
  agente humano/LLM interpreta el informe agregado, no el log crudo.
- Salidas de cada ejecución en
  `order-manager/build/pruebaCarga/<escenario>-<timestamp>/`:
  `pods.log`, la BBDD H2 (`bbdd.mv.db`) y `informe.md`.

---

## Fase 0 — Observabilidad estructurada en PROD

Único código de producción de todo el plan. Objetivo: que los momentos
relevantes del motor emitan eventos estructurados sin romper la pureza de
`business/**`.

### Tareas

1. Crear el puerto de salida
   `business.ordermanager.aplicacion.puerto.salida.PuertoObservadorEjecucion`
   (interfaz Java pura, vocabulario neutro del motor — nada de "saga").
   Métodos orientados a evento, con los datos justos; adaptar los nombres
   exactos a las señales reales del código (`ServicioContinuarOrden`,
   `SenalPaso`, `ReintentoOptimista`, `OrdenRoot`), sin inventar pasos que
   el código no tiene. Cobertura mínima de eventos:
   - reclamo ganado / reclamo perdido (con motivo: token vigente, no viva,
     colisión optimista)
   - colisión optimista (indicando en qué operación)
   - paso completado (con duración ms) / paso fallido (con intento y error)
   - reintento programado (con nº de intento y espera)
   - orden aparcada / orden finalizada (resultado OK o error)
2. Inyectarlo en `ServicioContinuarOrden` (constructor) e invocarlo en los
   puntos correspondientes, midiendo la duración de `ejecutarPaso` con
   `System.nanoTime()`. Revisar si otros servicios del motor
   (`ServicioTicketsSoporte`, `ServicioLimpiezaDatos`) tienen momentos del
   catálogo (ticket abierto, purga) y añadir métodos si aplica.
3. Adaptador `infraestructure.ordermanager.eventos.AdaptadorObservadorLog`:
   implementa el puerto con SLF4J, una línea por evento, formato
   `evento=<nombre> orden=<id> tipo=<tipo> ...` más `pod=<valor>` leído de
   la propiedad `ordermanager.pod` (por defecto `local`). Registrarlo en
   `ConfiguracionOrderManager`.
4. Eventos que ya nacen en infraestructura se loguean allí mismo con el
   mismo formato (sin pasar por el puerto): creación de tramitación
   (adaptador REST o caso de uso de entrada — donde ya haya frontera infra),
   respuesta de secundaria 2 registrada (`ConsumidorRespuestaSecundaria2`),
   ticket abierto (`AdaptadorTicketsLog`, ya loguea: adaptar al formato).
5. Documentar el catálogo exacto de eventos y su formato en
   `order-manager/src/pruebaCarga/resources/escenarios/README.md` (sección
   nueva "Catálogo de eventos del log") — el analizador de la fase 3 lo usa
   como contrato.
6. Tests: dobles en memoria del puerto en los unitarios de
   `ServicioContinuarOrden` (verificando qué evento se emite en cada rama);
   test unitario del adaptador (con un appender de lista de Logback,
   permitido en `src/test`: no es Spring) cubriendo el formato. 100%.
7. Diagramas: actualizar los `.puml` afectados por el puerto nuevo (como
   mínimo el de clases de aplicación del motor y el de infraestructura de
   eventos; revisar el índice `order-manager/docs/README.md` para
   identificarlos), regenerar `.png`, actualizar índice.

### Criterio de aceptación

- `./gradlew check` verde (incluye 100% y ArchUnit).
- `grep -rn "slf4j\|Logger" order-manager/src/main/java/com/ejemplo/app/business` sin resultados.
- Diagramas y PNG regenerados en el mismo commit.

---

## Fase 1 — Source set `pruebaCarga` en el build

### Tareas

1. En `order-manager/build.gradle`, replicar el patrón de `integrationTest`:
   source set `pruebaCarga` (`src/pruebaCarga/java` + `resources`) con
   `compileClasspath`/`runtimeClasspath` += `sourceSets.main.output`;
   `pruebaCargaImplementation.extendsFrom implementation` y añadir
   `com.h2database:h2` y `org.springframework.boot:spring-boot-starter-test`
   NO (no es un test): solo lo que necesite el lanzador (spring-boot ya
   viene de main; snakeyaml viene con spring-boot).
2. Task `pruebaCarga` de tipo `JavaExec`: main class el lanzador de la
   fase 2, `args` desde `-Pescenario` (obligatorio, fallar con mensaje claro
   si falta o el yml no existe). **No** engancharla a `check`.
3. Regla ArchUnit nueva en `ReglasArquitecturaTest`: nada de
   `com.ejemplo.app..` fuera de `..carga..` depende de `com.ejemplo.app.carga..`
   (documenta la frontera aunque el classpath ya la imponga). Aplicar la
   memoria del proyecto: si la regla tropieza con tests existentes por
   motivos ajenos, acotarla a producción antes que ampliar su alcance.
4. `.gitignore`: comprobar que `build/` ya está ignorado (las salidas de las
   ejecuciones cuelgan de ahí); añadirlo si no.

### Criterio de aceptación

- `./gradlew compilePruebaCargaJava` compila (con una clase lanzadora
  esqueleto si la fase 2 aún no existe).
- `./gradlew check` verde y NO ejecuta nada de pruebaCarga.
- `./gradlew pruebaCarga` sin `-Pescenario` falla con mensaje claro.

---

## Fase 2 — Harness: lanzador multi-pod, mocks y simulador Kafka

Todo bajo `order-manager/src/pruebaCarga/java/com/ejemplo/app/carga/`.

### Tareas

1. **`EscenarioCarga`** (record + carga desde yml con snakeyaml): mapea el
   esquema del `README.md` de escenarios. Validar campos obligatorios y
   rechazar claves desconocidas (typos en un yml no deben pasar en
   silencio).
2. **`LanzadorPruebaCarga`** (main):
   - Lee el escenario, crea `build/pruebaCarga/<nombre>-<timestamp>/`.
   - Configura el log a fichero `pods.log` de esa carpeta (además de
     consola), formato de una línea; cada pod aporta `ordermanager.pod=N`.
   - Arranca N `SpringApplication` (perfil `carga`,
     `web-application-type=none`), todos con
     `spring.datasource.url=jdbc:h2:file:<carpeta>/bbdd;MODE=Oracle` y las
     propiedades del bloque `motor:` del escenario. El primer pod inicializa
     el esquema con `order-manager/db/*.sql` (reutilizar el mecanismo que ya
     use `src/integrationTest` — mirar `application-test.yml`); los demás
     esperan a que esté listo.
   - Los crons de PROD (tickets, limpieza, purga, conciliación) no disparan
     dentro de una prueba corta: el perfil `carga` los acelera. Añadir al
     esquema de escenario los overrides necesarios (p. ej.
     `motor.tickets.cron`), actualizar README y los yml que los necesiten
     (`fallos-30` y `respuestas-perdidas` quieren tickets ≈ cada minuto).
   - **Inyector**: desde el contexto del pod 0, crea `tramitaciones` a
     `ritmo-por-segundo` invocando el caso de uso de creación (payloads
     sintéticos variados con la semilla).
   - **Parada**: deja de inyectar al agotar el total o `duracion`; espera a
     que no queden órdenes vivas o venza `drenaje-maximo`; cierra contextos
     ordenadamente; invoca el analizador (fase 3) y termina con exit code
     según su veredicto (0 invariantes OK, 1 violadas).
3. **Mocks REST** (`carga.mocks`): una implementación por puerto externo
   (`PuertoPaso1..8`, `PuertoSagaSecundaria1/3`, y los demás que el contexto
   exija — descubrir el conjunto completo arrancando el contexto; los dobles
   de `src/integrationTest` sirven de inventario). Comportamiento común
   (clase base): dormir latencia uniforme min–max, lanzar `RuntimeException`
   con probabilidad `tasa-fallo`, respetando overrides `por-puerto`.
   Registrados por una `@Configuration` del perfil `carga`.
4. **`SimuladorRespuestaSecundaria2`**: el mock de `PuertoSagaSecundaria2`,
   al ser invocado, programa (scheduler propio) la llamada a
   `CasoUsoRegistrarRespuestaSecundaria2` tras `retraso-ms`, salvo pérdida
   con probabilidad `tasa-perdida`. Excluir el consumidor Kafka real y la
   autoconfiguración de Kafka en el perfil `carga`.
5. Este código no lleva tests (no computa cobertura), pero sí se valida de
   punta a punta con el escenario `humo` (criterio de aceptación).

### Criterio de aceptación

- `./gradlew pruebaCarga -Pescenario=humo` termina solo (~5 min), exit 0,
  y la carpeta de salida contiene `pods.log` con eventos de al menos 2 pods
  distintos y la BBDD con todas las órdenes en estado terminal.
- `./gradlew check` sigue verde (PROD intacto en esta fase).

---

## Fase 3 — Analizador determinista + informe

`carga/analisis/`, invocado automáticamente al final de cada ejecución (y
ejecutable a mano sobre una carpeta de salida existente).

### Tareas

1. **`AnalizadorEjecucion`**: lee `pods.log` (parseo del formato
   `clave=valor` según el catálogo del README) y consulta la H2 de la
   carpeta por JDBC. Produce `informe.md` con:
   - **Veredicto** (primera línea: BUENO / MALO + por qué) e
     **invariantes** pasa/falla:
     - toda orden creada está en estado terminal, o aparcada con causa
       viva, o tiene ticket abierto (ninguna estancada sin dueño);
     - ningún solape de ejecución: entre `reclamo_ganado` y
       aparcar/finalizar/reintento de una orden no hay `reclamo_ganado` de
       otro pod para la misma orden (salvo lease vencido, que se señala);
     - los reintentos respetan la política (nº máximo de intentos);
     - tickets abiertos ⇔ órdenes que agotaron reintentos u órdenes
       aparcadas caducadas (ni de más ni de menos).
   - **Métricas**: throughput por minuto (creadas vs finalizadas), duración
     de saga p50/p95/máx, reclamos ganados/perdidos y % de colisión por
     pod, reintentos totales y por paso, distribución final de estados
     (SQL), profundidad de la cola de ejecutables a lo largo del tiempo.
   - **Anomalías**: minutos sin ningún reclamo habiendo pendientes (falta
     de ritmo), órdenes con duración > p99, pods desequilibrados.
2. Exit code del analizador = veredicto (lo usa el lanzador).
3. **`PROMPT-ANALISIS.md`** en `src/pruebaCarga/`: prompt plantilla para un
   agente LLM analista: rutas de la carpeta de salida, qué es cada fichero,
   cómo consultar la H2 (`jdbc:h2:file:...` con el shell de H2 del
   classpath), y la instrucción de partir del `informe.md` y bajar al log
   crudo/SQL solo para diagnosticar anomalías concretas.

### Criterio de aceptación

- `humo` vuelve a correr y su `informe.md` tiene veredicto, invariantes y
  métricas coherentes (creadas == finalizadas, 0 tickets).
- Romper un invariante a mano (editar una copia del log) hace fallar el
  analizador con exit 1 — probarlo puntualmente, sin dejar rastro.

---

## Fase 4 — Cierre

1. Añadir un escenario versionado `humo-contencion.yml` (5 min, 8 pods,
   mismo espíritu que `contencion-8-pods`; sumarlo a la matriz del README
   de escenarios) y ejecutarlo, para validar el harness con contención
   real en un tiempo asumible: debe haber
   `colision_optimista` en el log y el informe debe contarlas sin violar
   invariantes.
2. Documentación: sección "Pruebas de carga" en `order-manager/docs/README.md`
   enlazando el README de escenarios (cómo lanzar, qué escenarios hay, qué
   mide y qué no — copiar la advertencia de extrapolación). Los diagramas
   de arquitectura NO se tocan aquí (pruebaCarga no es PROD); solo si la
   fase 0 dejó algo pendiente.
3. Revisión final: `./gradlew check` + `pruebaCarga -Pescenario=humo` +
   `git status` limpio tras el último commit.

---

## Orquestación, commits y verificación

- **Antes de empezar**: si el árbol de trabajo tiene cambios sin commitear,
  hacer un commit baseline con ellos (flujo del proyecto) antes de la fase 0.
- **Un commit por fase**, mensaje en español (`Carga fase N: ...`),
  siempre con `./gradlew check` verde antes de commitear. Push al terminar
  todas las fases (`http.postBuffer` amplio ya configurado por los PNG).
- **Orden estricto** de fases (0 → 4); cada una asume la anterior terminada.
- Si una verificación falla, se corrige dentro de la misma fase; nunca se
  "aprueba" bajando umbrales, borrando tests o desactivando reglas ArchUnit.
- Presupuesto de sorpresas: si el código real contradice algo de este plan
  (nombres, mecanismos), manda el código; se adapta el plan al código, se
  anota la desviación en el informe final y se sigue. Lo que NO es
  adaptable: las reglas innegociables y las decisiones de diseño ya tomadas.
