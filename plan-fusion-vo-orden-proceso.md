# Plan: fusión orden+proceso, re-check de ejecutabilidad, VO-ización y guardas

Módulo: `order-manager`. Este plan lo ejecuta un agente coordinador lanzando
un subagente por fase (la fase 4 usa dos subagentes secuenciales). **Este
fichero es la fuente de verdad**: si una fase descubre algo que contradice
el plan, el coordinador lo anota y decide, no improvisa el subagente.

## Contexto: los dos defectos que motivan el cambio

**Defecto A (lectura mixta / torn read).**
`AdaptadorRepositorioOrden.cargar` lee el agregado en 3 SELECT separados
(`proceso` → satélite → `orden`) y solo la fila `orden` lleva `version`.
Bajo READ_COMMITTED, un commit ajeno entre el SELECT de `proceso` y el de
`orden` produce una lectura mixta: FSM de negocio vieja + fila de ejecución
fresca. Como la `version` leída es la fresca, el guardado pasa el candado
optimista y el pod re-ejecuta un paso ya hecho (llamada REST externa
duplicada). Observado y reproducido en 2 ejecuciones de
`pruebaCarga -Pescenario=rafaga-extrema`: sagas SECUNDARIA2 con el paso
`solicitar` ejecutado dos veces (dos respuestas Kafka con `mensaje_id`
distintos). Evidencia: órdenes `68cde3ec-…` y `88d9d14a-…` (run
`20260719T075012`), `d3ed2240-…` y `03137c04-…` (run `20260719T075729`).
Lo arregla la fusión de tablas (fase 2): una fila = una foto atómica.

**Defecto B (reclamo de orden no ejecutable / conciliación prematura).**
`aparcar` libera el token, y `reclamarToken` solo comprueba "¿viva? ¿token
vigente?" — NO re-comprueba `proximoReintentoEn <= ahora` sobre la fila
recién cargada. Un pod con la lista de candidatas obtenida un instante
antes puede reclamar una orden que otro pod acaba de aparcar y ejecutar su
paso actual antes de tiempo: en SECUNDARIA2, la conciliación REST se
disparó 3 horas antes de su ventana. Evidencia: orden `3475ce0f-…` (run
`20260719T075012`). La fusión NO arregla este defecto (ocurre incluso con
lectura consistente); lo arregla el re-check de la fase 3.

La VO-ización (fase 4) es una mejora de modelado independiente aprobada a
la vez: dentro del agregado, la única entidad pasa a ser la raíz.

## Reglas transversales (obligatorias en TODAS las fases)

- Cumplir CLAUDE.md al pie de la letra: 100% de cobertura (instrucción y
  rama) en cada commit, sin bajar el umbral jamás; diagramas `.puml` + PNG
  regenerados (skill `puml-to-png`) y `docs/README.md` actualizados **en el
  mismo commit** que el código que los desactualiza; prohibido
  `ON DELETE CASCADE`; capa business solo Java + jMolecules (+
  `jakarta.transaction.Transactional` en aplicación); nomenclatura en
  español.
- Un commit por fase (la fase 1 se comitea junto con la 2; la fase 4 es un
  único commit aunque la ejecuten dos subagentes). `./gradlew check` verde
  antes de cada commit. Push solo al final (`http.postBuffer` amplio: los
  PNG pesan).
- Los subagentes NO comitean: el coordinador verifica (`check` +
  inspección del diff) y comitea él.
- El esquema de `src/integrationTest` lo genera Hibernate desde las
  entidades (`ddl-auto: create-drop`): al fusionar entidades se fusiona
  solo; los DDL manuales que SÍ hay que tocar son `db/*.sql` (producción)
  y el harness (`InicializadorEsquemaH2`).
- El catálogo de eventos del log
  (`src/pruebaCarga/resources/escenarios/README.md`) es un contrato con el
  analizador: si una fase añade o cambia eventos/campos/motivos, actualiza
  catálogo y analizador en la misma fase.

## Fase 1 — Test que reproduce el defecto A (en ROJO)

Objetivo: un test de integración determinista que demuestre la doble
solicitud ANTES del fix. No se comitea en rojo: se comitea junto al fix de
la fase 2 (CLAUDE.md exige cambio+tests en el mismo commit y check verde).

- Ubicación: `src/integrationTest/java/com/ejemplo/app/infraestructure/ordermanager/persistencia/`
  (nuevo test, p. ej. `CargaConsistenteAgregadoIntegrationTest`), con el
  soporte en `testsoporte/` si hace falta. Reutilizar el patrón de dobles y
  de hilos reales (`ExecutorService` + `CountDownLatch`) del test de
  integración de concurrencia existente.
- Mecanismo: registrar un `StatementInspector` de Hibernate vía
  `spring.jpa.properties.hibernate.session_factory.statement_inspector`
  (clase de testsoporte con un hook estático intercambiable, no-op por
  defecto). El inspector, SOLO para el hilo B y la orden bajo prueba,
  bloquea en un latch justo antes del `SELECT ... FROM orden` (es decir,
  después de que B ya leyera `proceso`), hasta que el hilo A confirme su
  commit.
- Guion: persistir una orden SECUNDARIA2 en INICIAL, ejecutable ya. Hilo A:
  reclama y ejecuta el paso (solicitar + aparcar) y comitea. Hilo B: entra a
  reclamar con la pausa del inspector entre sus dos SELECT. Doble de
  `PuertoSagaSecundaria2` que cuenta invocaciones de `solicitar`.
- Aserción: `solicitar` se invoca EXACTAMENTE 1 vez y el intento de B
  termina en retirada (reclamo perdido / `ConcurrenciaOptimistaException`),
  la orden queda coherente. **Hoy debe FALLAR con 2 invocaciones** — el
  coordinador verifica el rojo y su motivo antes de pasar a fase 2.
- Nota de resiliencia: escribir la aserción sobre el comportamiento (nº de
  solicitudes, estado final), no sobre las sentencias SQL internas, para que
  el test sobreviva a la fusión (tras la fase 2 la pausa del inspector deja
  de crear el mix y el test pasa por la vía natural).

## Fase 2 — Fusión de tablas `orden` + `proceso` (fix del defecto A)

El dominio NO cambia en esta fase. Solo persistencia y esquema.

- `db/`:
  - `orden.sql` absorbe `tipo`, `external_id`, `estado` (mismos tipos y
    NOT NULL que hoy en `proceso.sql`) y el índice
    `idx_proceso_external_id` (renombrado a `idx_orden_external_id`);
    desaparecen `proceso.sql` y la FK `fk_orden_proceso`.
  - `proceso_auditoria.sql`: FK pasa a referenciar `orden(orden_id)`.
  - Los 4 `proceso_saga_*.sql`: FK pasa a referenciar `orden(orden_id)`.
    (Los nombres de tabla satélite y de `proceso_auditoria` NO cambian:
    minimiza la onda expansiva; anotar en el propio DDL por qué.)
- Entidades/repos JPA (`infraestructure/ordermanager/persistencia`):
  - `OrdenEntity` gana `tipo`, `externalId`, `estado` y la colección
    `auditoria` (`@ElementCollection` tal como está hoy en
    `ProcesoEntity`). Sigue siendo la única `@Version`.
  - Se eliminan `ProcesoEntity` y `ProcesoJpaRepository`;
    `findByExternalIdAndTipo` y `borrarAuditoriaPorIds` se mudan a
    `OrdenJpaRepository`.
  - `OrdenJpaRepository`: `RESUMEN_SELECT`, `buscarCandidatas` y
    `buscarTicketsPendientes` pierden el `JOIN proceso` (todas las columnas
    salen de `orden`).
- `AdaptadorRepositorioOrden`:
  - `cargar`: un único `findById` (foto atómica) + `mapeador.rearmar`
    (satélite después: cualquier commit intermedio bumpea la `version` de
    la fila ya leída y el `guardar` lo detecta — documentar esta regla en
    un comentario del adaptador).
  - `crear`: fila `orden` primero, satélite después (FK invertida).
  - `guardar`: un save + flush + `guardarContexto`.
  - `purgarFinalizadasAntesDe`: borrar auditoría → satélites → `orden`
    (explícito, sin cascadas).
  - La SPI `MapeadorProceso`/`DescriptorSoporteOrden` NO cambia de firma.
- Otros usuarios de `ProcesoJpaRepository`:
  `AdaptadorBusquedaTramitacion` (sagas/persistencia) pasa a
  `OrdenJpaRepository`; revisar `AdaptadorConsultaOrdenesSoporte`.
- Harness `src/pruebaCarga`: `InicializadorEsquemaH2` (quitar
  `proceso.sql` de la lista), `RepositorioAnalisisBbdd` (SQL sin JOIN),
  y menciones a las tablas en `PROMPT-ANALISIS.md` y
  `resources/escenarios/README.md`.
- Diagramas: `23-clases-infraestructura-persistencia`,
  `24-clases-infraestructura-saga` y los que citan la tabla/entidad
  (`01`, `09`, `26` — verificar con grep) + PNG + `docs/README.md`.
- Verificación de fase: el test de la fase 1 pasa a VERDE sin tocarlo;
  `./gradlew check` verde (100%). Commit único: test (fase 1) + fusión.

## Fase 3 — Re-check de ejecutabilidad en `reclamarToken` (fix del defecto B)

Fase pequeña y acotada al motor + catálogo de eventos.

- `OrdenRoot` (dominio): método de consulta (nombre en español, p. ej.
  `turnoVencido(Instant ahora)` / `esEjecutable(Instant ahora)`) que
  encapsula `proximoReintentoEn <= ahora` — espejo del predicado de
  `buscarEjecutables`, documentar que deben mantenerse alineados.
- `ServicioContinuarOrden.reclamarToken`: tras cargar y ANTES de comprobar
  el token, si la orden no está en turno de ejecución → reclamo perdido y
  `Optional.empty()`.
- `PuertoObservadorEjecucion.MotivoReclamoPerdido`: valor nuevo
  `NO_EJECUTABLE`. Verificar cómo lo serializa `AdaptadorObservadorLog`
  (debería salir gratis por nombre de enum).
- Contrato del harness: actualizar la fila de `reclamo_perdido` en el
  catálogo de eventos (`resources/escenarios/README.md`) con el motivo
  nuevo, y revisar con grep si `Metricas`/`Invariantes` del analizador
  enumeran los motivos (si agrupan genéricamente por valor, no hay cambio).
- Tests unitarios (src/test, sin Spring): el método nuevo de `OrdenRoot`;
  y en el servicio, con dobles en memoria: candidata cuyo
  `proximoReintentoEn` quedó en el futuro (otro actor la aparcó entre el
  barrido y el reclamo) → no se reclama, se observa `NO_EJECUTABLE`, no se
  ejecuta ningún paso. Mantener 100%.
- Diagramas: revisar si alguno documenta los motivos de reclamo perdido o
  el flujo de `reclamarToken` (probablemente `17` y algún diagrama de
  secuencia); actualizar solo los afectados + PNG + README de docs.
- `./gradlew check` verde. Commit.

## Fase 4 — VO-ización de `Proceso` (dos subagentes, UN commit)

La raíz `OrdenRoot` sigue siendo la entidad con identidad y ciclo de vida;
`Proceso` y sus 4 subclases pasan a ser valores inmutables. El estado
intermedio tras la subfase 4a NO compila — es esperado; el commit llega al
final de 4b con todo verde.

### Subfase 4a — dominio (subagente 1)

- `Proceso<E>` (`business/ordermanager/dominio`): `@ValueObject` (fuera
  `@Entity` y `@Identity`; `id`/`externalId` quedan como atributos
  normales), todos los campos `final`, lista de auditoría inmutable,
  `equals`/`hashCode` por valor en la base (subclases incluyen su
  contexto). Las mutaciones se vuelven transiciones que devuelven instancia
  nueva: `aplicarYAvanzar`, `marcarPasoActualOkManual`, `auditar` y las
  específicas de cada saga (`cancelar`, `compensacionCompletada`,
  `solicitudEnviada`, `respuestaRecibida`, `volverASolicitar`, …).
- Las 4 subclases (`SagaPrincipal`, `SagaSecundaria1/2/3`) reescritas al
  estilo copia, conservando nombres de método y semántica de negocio.
- `OrdenRoot`: campo `proceso` no-final + un único método de reemplazo
  (p. ej. `reemplazarProceso(Proceso<?>)`), con javadoc: la raíz es la
  dueña del valor y el único punto de sustitución.
- Tests unitarios de dominio (`src/test/.../business/**`) ajustados al
  estilo copia (aserciones sobre la instancia devuelta).

### Subfase 4b — llamantes, mapeadores y diagramas (subagente 2)

- Aplicación: `ServicioSagaPrincipal`, `ServicioSagaSecundaria1/2/3`,
  `ServicioRegistrarRespuestaSecundaria2`, `ServicioCancelarTramitacion`,
  el marcar-OK de soporte y los que descubra el compilador — patrón:
  `orden.reemplazarProceso(saga.transicion(...))` antes de `guardar`.
- Infraestructura: los `Soporte*` (`rearmar` construye instancias
  inmutables; `guardarContexto` no cambia de firma) y cualquier llamante
  restante hasta compilar.
- Tests de aplicación/infra ajustados; `./gradlew check` verde (100%).
- Diagramas: `13`-`16`, `25` (estereotipos VO y transiciones) + `14`/`17`
  si muestran `Proceso` + PNG + README de docs.
- Commit único de la fase 4.

## Fase 5 — `jmolecules-archunit` en `ReglasArquitecturaTest`

- `build.gradle`: `testImplementation 'org.jmolecules.integrations:jmolecules-archunit'`.
  Comprobar si el BOM `jmolecules-bom:2023.1.7` gestiona su versión; si no,
  fijarla explícita (y valorar subir el BOM en un cambio aparte, no aquí).
- `ReglasArquitecturaTest`: añadir las reglas DDD de jMolecules
  (`JMoleculesDddRules.all()` o las individuales) sobre las clases de
  producción. Si fallan por código de producción legítimo, se arregla el
  código; si fallan solo por clases de test, acotar a producción
  (`DO_NOT_INCLUDE_TESTS`), como ya se hizo con otras reglas de frontera.
  Tras la fase 4, los VOs inmutables deben pasar limpio.
- `./gradlew check` verde. Commit.

## Fase 6 — 5º invariante del analizador de carga (harness)

Red de regresión determinista para el defecto A a nivel de prueba de carga.

- `Invariantes` (`src/pruebaCarga/.../analisis`): invariante nuevo
  "Sin solicitudes duplicadas en SECUNDARIA2" — para cada orden
  SECUNDARIA2, el nº de eventos `respuesta_secundaria2_registrada` (por
  `mensaje_id` distinto) debe ser `<= 1 + nº de respuestas exito=false
  previas de esa orden`. Documentar en el código que es una cota superior:
  con `tasa-perdida > 0` puede no detectar (respuestas que nunca llegan),
  pero nunca da falso positivo. Violación → listar orden, mensaje_ids y
  timestamps.
- `InformeMarkdown`: renderizar el invariante 5 con el mismo formato que
  los otros 4 (pasa/falla + detalle de violaciones).
- Actualizar TODA mención a "los 4 invariantes" en el harness
  (`PROMPT-ANALISIS.md`, README de escenarios, javadoc de
  `AnalizadorEjecucion`/`Invariantes` — localizar con grep).
- Tests del invariante siguiendo el patrón de tests existente del
  analizador (localizarlo; si el source set `pruebaCarga` no entra en el
  jacoco de producción, mantener el patrón que ya siga el proyecto — no
  inventar infraestructura nueva de cobertura).
- `./gradlew check` verde. Commit.

## Fase 7 — Verificación de punta a punta y cierre

- `./gradlew clean check` desde cero.
- Ejecutar `./gradlew pruebaCarga -Pescenario=rafaga-extrema` (≈2-3 min) y
  verificar: veredicto BUENO con **5 invariantes en PASA** en `informe.md`
  (el 5º ya activo), y en `pods-compacto.log` ninguna SECUNDARIA2 con dos
  `respuesta_secundaria2_registrada` sin `exito=false` previo. Una sola
  ejecución no prueba ausencia, pero con los tests deterministas de las
  fases 1 y 3 en verde es confirmación suficiente.
- Repasar `docs/README.md` (índice al día) y push final.

## Fuera de alcance (decisiones ya tomadas)

- Opción C (fusionar satélites): DESCARTADA — rompería la SPI abierta por
  tipo de orden.
