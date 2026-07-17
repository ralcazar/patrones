# Plan: separar el motor `ordermanager` (genérico) de las `sagas` (concretas)

> Documento de trabajo para implementar por pasos (cada paso lo ejecuta un agente).
> Al terminar un paso: `./gradlew test` verde, commit, y marcar la casilla en **Estado**.

## Estado

- [x] Paso 1 — Mover sagas a sus paquetes (git mv + imports, sin renombrar)
- [x] Paso 2 — SPI de persistencia `MapeadorProceso` + partir la configuración
- [x] Paso 3 — Extraer soporte saga-específico (cancelación, vista, `DescriptorSoporteOrden`)
- [x] Paso 4 — `TipoSaga` (enum) → `TipoOrden` (record) + regla ArchUnit de frontera
- [ ] Paso 5 — Renombrados neutros del motor + aplanar paquetes
- [ ] Paso 6 — BD/SQL y claves de configuración
- [ ] Paso 7 — Diagramas, PNG, READMEs y CLAUDE.md + push

## Contexto

Poder llevarse el "order manager" (motor de órdenes: agregado, planificador, workers, persistencia, soporte/tickets/limpieza) a otras aplicaciones y definir allí otros tipos de orden (sagas u otra cosa) **sin tocar el código del motor**. Hoy todo vive mezclado bajo `business/ordermanager` e `infraestructure/ordermanager`, y el motor conoce las 4 sagas concretas en varios puntos.

Decisiones tomadas con el usuario:
- **Solo paquetes** dentro del módulo Gradle actual (no multi-módulo). La frontera la impone ArchUnit.
- **Terminología neutra en el motor**: nada de "saga" en los paquetes `ordermanager`. `TipoSaga` → `TipoOrden` (record VO con String), `SagaId` → `OrdenId`, base abstracta `Saga<E>` → **`Proceso<E>`** (y tabla `saga` → `proceso`, columna `saga_id` → `orden_id`).
- Nomenclatura siempre en español.

Puntos donde hoy el motor conoce las sagas (lo que se rompe):
1. `dominio/comun/TipoSaga.java` — enum cerrado (PRINCIPAL, SECUNDARIA1..3).
2. `AdaptadorRepositorioOrden` — switches `entidadSagaDe`/`sagaDesde` que importan las 4 sagas y sus VOs.
3. `ConfiguracionAplicacion` — cablea a mano los 4 `ServicioSaga*` y el `Map.of(...)`.
4. `ContextoArranque` (en `dominio/comun`) enumera las 3 hijas; `RefPaso1/5/7` son de sagas.
5. `ServicioIniciarTramitacion`, `ServicioRegistrarRespuestaSecundaria2` + consumer Kafka: son de sagas.
6. `ServicioSoporteSagas.cancelarPrincipal` (cast a `SagaPrincipal`) y `AdaptadorConsultaSagasSoporte` (3 switches FSM + `vistaTramitacion`).

## Estructura final de paquetes

```
com.ejemplo.app
├── business
│   ├── ordermanager            ← motor, cero terminología saga
│   │   ├── dominio             (se aplana: desaparece dominio/comun)
│   │   └── aplicacion
│   │       ├── puerto/{entrada,salida}
│   │       └── servicio        (se aplana: desaparece servicio/comun)
│   └── sagas                   ← aquí "saga" es vocabulario correcto
│       ├── dominio/{comun,sagaprincipal,sagasecundaria1..3}
│       └── aplicacion/{puerto/{entrada,salida}, servicio/{comun,sagaprincipal,sagasecundaria1..3}}
└── infraestructure
    ├── ordermanager
    │   ├── ConfiguracionOrderManager
    │   ├── persistencia        (+ SPIs MapeadorProceso, DescriptorSoporteOrden)
    │   ├── eventos             (AdaptadorTicketsLog)
    │   └── programados         (planificadores, worker, config async)
    └── sagas
        ├── ConfiguracionSagas
        ├── persistencia        (mapeadores/descriptores de las 4 sagas)
        └── eventos             (ConsumidorRespuestaSecundaria2)
```

## Reparto y renombrados

### Queda en `business.ordermanager` (renombrando lo que dice "saga")

| Actual | Nuevo |
|---|---|
| `SagaId` | `OrdenId` |
| `TipoSaga` (enum cerrado) | `TipoOrden` — `record TipoOrden(String valor)` con validación no-nulo/no-vacío |
| `Saga<E>` (base abstracta) | `Proceso<E>` |
| `OrdenRoot` | igual; `saga()` → `proceso()`, `sagaId()` → `id()`, mensajes/javadoc neutros |
| `SagaYaCompletadaException` | `OrdenYaCompletadaException` |
| `CasoUsoContinuarSaga` / `ServicioContinuarSaga` | `CasoUsoContinuarOrden` / `ServicioContinuarOrden` |
| SPI `ServicioSaga` | `ProcesadorOrden` (`TipoOrden tipo(); SenalPaso ejecutarPaso(OrdenRoot)`) |
| `CasoUsoIntervenirSaga` | `CasoUsoIntervenirOrden` (SIN `cancelarPrincipal`, que se va a sagas) |
| `CasoUsoConsultarSagasSoporte` | `CasoUsoConsultarOrdenesSoporte` (records `OrdenResumen`, `OrdenDetalle`, `FiltroOrdenes`; `VistaTramitacion` se va a sagas) |
| `ServicioSoporteSagas` | `ServicioSoporteOrdenes` (sin la cancelación) |
| `PuertoConsultaSagasSoporte` | `PuertoConsultaOrdenesSoporte` |
| `PuertoSagasTicketPendiente` | `PuertoOrdenesTicketPendiente` (record `OrdenTicketPendiente`) |

Sin renombrar: `PoliticaReintentos`, `ResultadoOrden`, `ExternalId`, `MensajeId`, `UsuarioSoporte`, `AuditoriaIntervencion`, `MotivoFallo`, `ComandoPaso`/`ResultadoPaso`, `SenalPaso`, `ReintentoOptimista`, `RepositorioOrden` (su `CandidataOrden` pasa a `(OrdenId, TipoOrden)`), `PuertoTicketsSoporte`, `PuertoMensajesProcesados`, `ServicioTicketsSoporte`, `ServicioLimpiezaDatos`, `CasoUsoAbrirTicketsPendientes`, `CasoUsoLimpiarDatosAntiguos`, excepciones restantes.

### Se mueve a `business.sagas`

- **Dominio**: `sagaprincipal/**`, `sagasecundaria1..3/**` completos; de `dominio/comun`: `ContextoArranque`, `RefPaso1`, `RefPaso5`, `RefPaso7` (→ `sagas.dominio.comun`) y `PuntoNoRetornoSuperadoException` (→ con la principal). Cada saga declara su constante: `public static final TipoOrden TIPO = new TipoOrden("PRINCIPAL")` (ídem `SECUNDARIA1..3`) — los valores en BD no cambian.
- **Aplicación**: `ServicioSagaPrincipal` (implementa `ProcesadorOrden`; su `crearHijas` con switch concreto→concreto se queda tal cual), `ServicioSagaSecundaria1..3`, `ServicioIniciarTramitacion`/`CasoUsoIniciarTramitacion`, `ServicioRegistrarRespuestaSecundaria2`/`CasoUsoRegistrarRespuestaSecundaria2`; puertos `PuertoPaso1..8`, `PuertoSagaSecundaria1..3`, `PuertoConciliacionSecundaria2`.
- **Nuevos** (extraídos del soporte genérico, que hoy hace cast a `SagaPrincipal` y monta la vista por tipo):
  - `CasoUsoCancelarTramitacion` / `ServicioCancelarTramitacion` (el `cancelarPrincipal` actual).
  - `CasoUsoVistaTramitacion` / `ServicioVistaTramitacion` (compone `VistaTramitacion` a partir de `PuertoConsultaOrdenesSoporte.porExternalId(...)`, particionando por `SagaPrincipal.TIPO`).

### Infra: `infraestructure.ordermanager` (genérica)

`OrdenEntity`, `SagaEntity`→`ProcesoEntity`, `SagaJpaRepository`→`ProcesoJpaRepository`, `OrdenJpaRepository`, `AdaptadorRepositorioOrden` (queda 100 % genérico, ver SPI), `ContextoCodec` (ya es genérico Map↔JSON, se queda), `CandidataFila`, `SagaResumenFila`→`OrdenResumenFila`, `TicketPendienteFila`, `AdaptadorConsultaSagasSoporte`→`AdaptadorConsultaOrdenesSoporte` (sin switches, delega en `DescriptorSoporteOrden`), `AdaptadorSagasTicketPendiente`→`AdaptadorOrdenesTicketPendiente`, `AuditoriaEntity`, `AdaptadorTicketsLog`, `programados/**` sin renombrar.

### Infra: `infraestructure.sagas`

`ConsumidorRespuestaSecundaria2` (Kafka), `ConfiguracionSagas`, y en `persistencia` una clase por saga que implemente las dos SPI (p. ej. `SoporteSagaPrincipal implements MapeadorProceso, DescriptorSoporteOrden`, ídem secundarias 1–3), absorbiendo los `contextoDe*`/`*Desde`/`ponerSiNoNulo`/`refONull` de `AdaptadorRepositorioOrden` y los switches FSM de `AdaptadorConsultaSagasSoporte`. Claves JSON del contexto (`refPaso1`…) sin cambios.

## Nuevas SPI (los 3 puntos de extensión)

**1. Procesamiento** (ya casi existe, en `business.ordermanager.aplicacion.servicio`):
```java
public interface ProcesadorOrden {
    TipoOrden tipo();
    SenalPaso ejecutarPaso(OrdenRoot orden);
}
```
`ServicioContinuarOrden` conserva su `Map<TipoOrden, ProcesadorOrden>`; si `get(tipo)` es null → `IllegalStateException` con mensaje claro.

**2. Mapeo de persistencia por tipo** (en `infraestructure.ordermanager.persistencia` — la forma persistible es contrato de persistencia, no de negocio; la dirección infra/sagas → infra/ordermanager → business/ordermanager es legal):
```java
public interface MapeadorProceso {
    TipoOrden tipo();
    ProcesoPersistible desarmar(Proceso<?> proceso);
    Proceso<?> rearmar(OrdenId id, ExternalId externalId, String estado,
                       Map<String, String> contexto, List<AuditoriaIntervencion> auditoria);
    record ProcesoPersistible(String estado, Map<String, String> contexto) {}
}
```
`AdaptadorRepositorioOrden` recibe `List<MapeadorProceso>`, indexa por `tipo().valor()` y elimina sus dos switches (`entidadSagaDe`, `sagaDesde`). La auditoría y el external_id los sigue mapeando el adaptador (es común a todos los tipos).

**3. Modelo de lectura de soporte** (mismo paquete):
```java
public interface DescriptorSoporteOrden {
    TipoOrden tipo();
    String pasoPendiente(String estado);          // null si ya no avanza
    boolean datosManualesObligatorios(String estado);
    boolean cancelable(String estado);
}
```
`AdaptadorConsultaOrdenesSoporte` recibe `List<DescriptorSoporteOrden>` y elimina sus 3 switches; `vistaTramitacion` sale del puerto genérico (queda `porExternalId`).

## Configuración Spring

- **`ConfiguracionOrderManager`** (infra/ordermanager): `politicaReintentos`, `servicioContinuarOrden(List<ProcesadorOrden> procesadores, ...)` construyendo el mapa con `toUnmodifiableMap(ProcesadorOrden::tipo, p -> p)` (tipo duplicado → falla el arranque), `servicioSoporteOrdenes`, `servicioTicketsSoporte`, `servicioLimpiezaDatos`. Mantener el patrón `@Lazy self` tal cual está hoy en `ConfiguracionAplicacion`.
- **`ConfiguracionSagas`** (infra/sagas): los 4 `ServicioSaga*` (con su `@Lazy self` del tipo concreto — el patrón funciona igual repartido en dos `@Configuration`), `servicioIniciarTramitacion`, `servicioRegistrarRespuestaSecundaria2`, `servicioCancelarTramitacion`, `servicioVistaTramitacion`. Los `SoporteSaga*` (mapeador+descriptor) como `@Bean` aquí o `@Component`.
- **`application.yml`**: claves del motor bajo `ordermanager.*` (`orden.lease` → `ordermanager.lease`, `orden.planificador.*` → `ordermanager.planificador.*`) y de sagas bajo `sagas.*` (`ordermanager.topics.respuesta-secundaria2` → `sagas.topics.respuesta-secundaria2`, actualizar el `@KafkaListener` y los `@Value`/defaults afectados).

## BD / SQL (proyecto de patrones, sin datos productivos: se reescriben los scripts)

| Actual | Nuevo |
|---|---|
| tabla `saga` (`db/saga.sql`) | tabla `proceso` (`db/proceso.sql`) |
| tabla `saga_auditoria` | `proceso_auditoria` |
| columna `saga_id` (3 tablas) | `orden_id` |
| columna `orden_secuencia` | `secuencia` |
| constraints/índices `pk_saga`, `fk_orden_saga`, … | `pk_proceso`, `fk_orden_proceso`, … |
| valores de `tipo` y claves JSON de `contexto` | **sin cambios** |

Impacto: anotaciones `@Table`/`@Column` de las 3 entidades, queries nativas de `OrdenJpaRepository` (alias `sagaId` → `ordenId` y getters de `CandidataFila`/`OrdenResumenFila`/`TicketPendienteFila`), `db/orden.sql`, `db/README.md`. `application-test.yml` no cambia (H2 `create-drop`).

## Tests

- **Motor** (renombrados, quedan 100 % neutros): `ServicioContinuarSagaTest`→`ServicioContinuarOrdenTest`, fake `ServicioSagaFalso`→`ProcesadorOrdenFalso` con `new TipoOrden("FALSO")` (demuestra que el motor admite tipos nuevos sin tocarlo); `OrdenRootTest`, `PoliticaReintentosTest`, `ServicioTicketsSoporteTest`, `TrabajadorContinuacionTest`, `RepositorioOrdenEnMemoria`.
- **Se mueven a sagas**: `SagaPrincipalTest`, `SagaSecundaria2Test`, `ServicioSagaPrincipalTest`, `ServicioSagaSecundaria2Test`, `ServicioRegistrarRespuestaSecundaria2Test`, y `FronteraTransaccionalIntegrationTest` (su `ContextoTest` debe declarar el mapeador de secundaria3 para que `AdaptadorRepositorioOrden` arranque).
- **`ReglasArquitecturaTest`** (`order-manager/src/test/java/com/ejemplo/app/ReglasArquitecturaTest.java`):
  - Actualizar: `soloAplicacionUsaTransactional` → paquete `..business..dominio..` (ahora hay dos dominios); `ordenRootYSagaVivenEnElDominio` → `OrdenRoot`/`Proceso` en `business.ordermanager.dominio`.
  - **Nueva regla frontera (la clave del refactor)**: `noClasses().that().resideInAnyPackage("..business.ordermanager..", "..infraestructure.ordermanager..").should().dependOnClassesThat().resideInAnyPackage("..business.sagas..", "..infraestructure.sagas..")`.
  - Nueva regla de vocabulario: ninguna clase de `..ordermanager..` con nombre que contenga "Saga".

## Docs (regla CLAUDE.md: mismo cambio, .puml + .png juntos, skill `puml-to-png`)

- Actualizar nombres de clases/paquetes en las secuencias 01–09 y en 13, 17, 22, 23, 24; 15/16/18–21 cambian poco (paquete + herencia `Proceso<E>`).
- `14-clases-dominio-comun.puml` se parte: `14-clases-dominio-ordermanager.puml` + nuevo `25-clases-dominio-comun-sagas.puml` (`ContextoArranque`, `RefPaso1/5/7`).
- 23 incorpora las SPI `MapeadorProceso`/`DescriptorSoporteOrden`; 24 incorpora `ConfiguracionSagas` y la nueva frontera de paquetes.
- Actualizar `docs/README.md`, `CLAUDE.md` (rutas/nombres citados: `ConfiguracionAplicacion`, `ServicioSaga*`, ruta del consumer; añadir la regla de frontera ordermanager↛sagas), `README.md` y `db/README.md`.

## Orden de ejecución (pasos compilables; `./gradlew test` verde tras cada uno; commits separados, `git mv` para preservar historia)

1. **Mover sagas a sus paquetes** (solo `git mv` + imports, sin renombrar): dominios de saga, `ContextoArranque`+`RefPaso1/5/7`+`PuntoNoRetornoSuperadoException`, servicios/casos de uso/puertos de saga, consumer Kafka, y sus tests espejo. El motor aún importa sagas: compila, la frontera todavía no se impone.
2. **SPI de persistencia + partir configuración**: `MapeadorProceso` (aún con el enum `TipoSaga` como clave) + 4 implementaciones en infra/sagas; adelgazar `AdaptadorRepositorioOrden`; partir `ConfiguracionAplicacion` en `ConfiguracionOrderManager` + `ConfiguracionSagas`.
3. **Extraer soporte saga-específico**: `ServicioCancelarTramitacion`, `ServicioVistaTramitacion`, SPI `DescriptorSoporteOrden` + implementaciones; limpiar adaptador de consulta y casos de uso.
4. **`TipoSaga` → `TipoOrden`**: record en el motor, constantes `TIPO` en cada saga, sustituir usos, borrar el enum. **Activar aquí la regla ArchUnit de frontera** para que vigile el resto.
5. **Renombrados neutros del motor** por lotes: (a) `OrdenId`, `Proceso`, `OrdenYaCompletadaException`; (b) servicios/casos de uso/puertos (`ServicioContinuarOrden`, `ProcesadorOrden`, `ServicioSoporteOrdenes`, …); (c) infra (`ProcesoEntity`, `ProcesoJpaRepository`, `OrdenResumenFila`, adaptadores, fakes). Aplanar `dominio/comun`→`dominio` y `servicio/comun`→`servicio`. Actualizar reglas ArchUnit de nombres + regla de vocabulario.
   **Pendiente de Paso 4**: la regla de frontera `ordermanagerNoDependeDeSagas` hoy solo analiza producción (`ImportOption.DO_NOT_INCLUDE_TESTS`) porque `ServicioContinuarSagaTest`, `OrdenRootTest`, `ServicioTicketsSoporteTest`, `RepositorioOrdenEnMemoria` y `FronteraTransaccionalIntegrationTest` (todos bajo paquetes `ordermanager`) todavía construyen sagas concretas para sus fixtures. Al introducir aquí los dobles genéricos (`ProcesadorOrdenFalso` con `TipoOrden("FALSO")`, una `Saga` de test genérica) y mover `FronteraTransaccionalIntegrationTest` a `infraestructure.sagas`, volver la regla a un `@ArchTest` de campo normal (sin el `@Test` manual ni el `DO_NOT_INCLUDE_TESTS`) para que también vigile los tests.
6. **BD y claves de configuración**: scripts `db/*.sql`, anotaciones JPA, queries nativas, proyecciones; claves `ordermanager.*`/`sagas.*` en yml + `@Value`.
7. **Docs**: `.puml` + regenerar PNG (skill `puml-to-png`) + `docs/README.md` + `CLAUDE.md` + READMEs. Commit y push final.

## Riesgos a vigilar

- El `@Lazy self` al partir la configuración — cubierto por `FronteraTransaccionalIntegrationTest`.
- Renombrados masivos con sed tocando `.puml` antes de tiempo — docs se tocan solo en el paso 7.
- `ReintentoOptimista` y `ContextoCodec` son package-private — mantener consumidores en el mismo paquete o abrirlos dentro del motor.
- Mensajes de log/excepción del motor que digan "saga" (p. ej. `"No existe la saga "` en `AdaptadorRepositorioOrden.cargar`) — pasarlos a vocabulario neutro.

## Verificación final

1. `./gradlew test` verde tras cada paso (unitarios + `FronteraTransaccionalIntegrationTest` con H2 valida el mapeo JPA nuevo y el proxy transaccional).
2. `ReglasArquitecturaTest` verde con las reglas nuevas — en particular la de frontera ordermanager↛sagas.
3. Prueba de portabilidad: `grep -rn "\.sagas\." order-manager/src/main/java/com/ejemplo/app/business/ordermanager order-manager/src/main/java/com/ejemplo/app/infraestructure/ordermanager` no devuelve nada; el fake `ProcesadorOrdenFalso` con `TipoOrden("FALSO")` demuestra que se registra un tipo nuevo sin tocar el motor.
4. PNG regenerados y `docs/README.md` coherente con los diagramas.
