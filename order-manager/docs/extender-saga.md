# Cómo extender el motor de órdenes con una saga nueva

Esta guía explica qué hay que escribir para añadir un tipo de orden nuevo
(una saga nueva, o cualquier otro proceso de negocio que quiera apoyarse en
el motor) **sin tocar una sola línea de `business.ordermanager` ni de
`infraestructure.ordermanager`**. El motor es genérico en el tipo de orden a
propósito (ver `CLAUDE.md`, sección "Restricción de arquitectura:
ordermanager ↛ sagas"); toda saga concreta vive en `business.sagas` /
`infraestructure.sagas` y se registra ante el motor a través de 3 puntos de
extensión (SPI). Esta regla la hace ejecutable
`ReglasArquitecturaTest.ordermanagerNoDependeDeSagas`: si al añadir tu saga
el motor acaba dependiendo de ella, el test lo rechaza y el código está mal
ubicado, no el test.

Antes de seguir, lee en `CLAUDE.md` las secciones "Restricción de
arquitectura: entrada → aplicación → salida", "order-manager: pureza de las
capas business" y "Cobertura de tests: 100% + separación unit/integración":
esta guía asume que ya las conoces y no las repite.

## Las 3 SPI

El motor descubre las implementaciones concretas porque Spring se las
inyecta como listas (`List<ProcesadorOrden>`, `List<MapeadorProceso>`,
`List<DescriptorSoporteOrden>`) y las indexa por `tipo()`; el motor nunca
importa ni nombra ninguna clase de tu saga.

| SPI | Paquete | Responsabilidad |
|---|---|---|
| [`ProcesadorOrden`](../src/main/java/com/ejemplo/app/business/ordermanager/aplicacion/servicio/ProcesadorOrden.java) | `business.ordermanager.aplicacion.servicio` | Ejecuta un paso de tu tipo de orden: recibe el agregado ya cargado, hace el I/O externo fuera de transacción y aplica el resultado + guarda dentro de una transacción. |
| [`MapeadorProceso`](../src/main/java/com/ejemplo/app/infraestructure/ordermanager/persistencia/MapeadorProceso.java) | `infraestructure.ordermanager.persistencia` | Persiste y rearma el contexto de tu `Proceso<?>` en SU tabla satélite (una tabla por tipo de orden, PK = `orden_id`). |
| [`DescriptorSoporteOrden`](../src/main/java/com/ejemplo/app/infraestructure/ordermanager/persistencia/DescriptorSoporteOrden.java) | `infraestructure.ordermanager.persistencia` | Deriva, a partir del estado de la FSM, el paso pendiente / si es cancelable / si exige datos manuales, para la pantalla de soporte (sin cargar el agregado). |

`MapeadorProceso` y `DescriptorSoporteOrden` casi siempre los implementa la
misma clase (ver `SoporteSagaPrincipal`/`SoporteSagaSecundaria1/2/3`, cada una
en su propio paquete `infraestructure.sagas.<tutipo>.persistencia`), porque
ambas leen el mismo estado.
`ProcesadorOrden` en cambio vive en la capa de aplicación (`business.sagas`),
porque orquesta casos de uso y puertos de salida, no persistencia.

Las 4 sagas ya implementadas (`SagaPrincipal`, `SagaSecundaria1/2/3`) son la
referencia: cópialas como plantilla. El resto de esta guía usa
`SagaSecundaria3` (un único paso, sin compensación: el caso más simple) y
`SagaPrincipal` (multi-paso, con compensación y creación de sagas hijas)
como ejemplos concretos.

## 1. Dominio (`business.<tucontexto>.dominio.<tutipo>`)

Todo Java puro + jMolecules (`ReglasArquitecturaTest.businessSinFrameworks`
lo hace cumplir sobre producción y tests). Escribe, siguiendo el paquete
`sagasecundaria3` como plantilla mínima:

1. **`TipoOrden TIPO`** — una constante `public static final TipoOrden TIPO
   = new TipoOrden("MI_TIPO")` en tu clase `Proceso`. `TipoOrden` es un VO
   abierto (no un enum cerrado) precisamente para que puedas definir tipos
   nuevos sin tocar `ordermanager.dominio`.
2. **El enum de estados de tu FSM** (equivalente a `EstadoSagaSecundaria3` /
   `EstadoSagaPrincipal`): el orden de declaración de los estados debe seguir
   el flujo normal; los estados de compensación (si los hay) van al final.
3. **`ComandoPasoMiTipo`** — interfaz `sealed` que extiende `ComandoPaso`
   (marcadora del motor), con un `record` por paso que lleva justo los datos
   que ese paso necesita (ver `ComandoPasoPrincipal`/`ComandoPasoSecundaria3`).
   Si algún paso es compensable, añade sus `CompensarPasoN` aquí también
   (ver `ComandoPasoPrincipal.CompensarPaso1/2`).
4. **`ResultadoPasoMiTipo`** — interfaz `sealed` que extiende `ResultadoPaso`,
   con un `record` por lo que cada paso produce.
5. **La clase `Proceso`** — `extends Proceso<TuEnumEstado>`, anotada
   `@ValueObject` e inmutable: cada transición devuelve una instancia nueva
   (nunca mutes `this`). Implementa los 4 métodos abstractos de `Proceso`:
   - `tipo()` → tu constante `TIPO`.
   - `comandoActual()` → deriva el comando del paso pendiente a partir del
     estado (switch exhaustivo sobre el enum).
   - `aplicarYAvanzar(ResultadoPaso)` → hace un pattern-match a tu
     `ResultadoPasoMiTipo`, actualiza el contexto acumulado y calcula el
     siguiente estado.
   - `terminada()` → true en los estados finales (éxito o cancelado).
   - `marcarPasoActualOkManual(...)` → soporte marca OK a mano el paso
     pendiente; lanza `PasoNoIntervenibleException`/`DatosManualesRequeridosException`
     (ambas ya en `ordermanager.dominio`, reutilízalas) si no procede.
   Añade un `crear(...)` (constructor de alta) y un `rehidratar(...)` (para
   el adaptador de persistencia, ver más abajo).
6. Si tu saga la arranca otra (como las 3 secundarias arrancan de la
   principal), define el contexto de arranque recortado como un `record`
   dentro de un `sealed interface` compartido (ver
   `business.sagas.dominio.comun.ContextoArranque`): solo los datos que tu
   saga necesita, nunca una referencia a la saga que la originó.

Reloj determinista: ningún método de dominio llama a `Instant.now()`; todo
lo que dependa del tiempo lo recibe como parámetro `Instant ahora` desde la
capa de aplicación.

## 2. Aplicación (`business.<tucontexto>.aplicacion`)

También Java puro + jMolecules, con la única excepción de
`jakarta.transaction.Transactional` para marcar la frontera transaccional
(ver `CLAUDE.md`).

1. **Puertos de salida** (`aplicacion.puerto.salida`) — una interfaz por
   servicio externo que invoque tu saga, con un método `ejecutar(cmd)` (y
   `compensar(cmd)` si el paso es compensable). Ver `PuertoPaso1`..`PuertoPaso8`
   o, más simple, `PuertoSagaSecundaria3`. El adaptador que la implemente
   (en infraestructura) es quien lanza `ExcepcionServicioExterno` ante fallo;
   el puerto en sí no conoce HTTP ni ningún detalle de transporte.
2. **El `ProcesadorOrden`** (`aplicacion.servicio`, anotado `@Service`) —
   implementa la SPI del motor:
   - `tipo()` devuelve tu constante `TIPO`.
   - `ejecutarPaso(OrdenRoot orden)` recibe el agregado **ya cargado** por
     `ServicioContinuarOrden` (nunca lo recargues tú): haz el cast a tu
     `Proceso`, invoca el puerto de salida (I/O **fuera** de transacción) y
     aplica el resultado dentro de un método `@Transactional` que reemplaza
     el `Proceso` en el `OrdenRoot` (`orden.reemplazarProceso(...)`) y lo
     guarda una única vez con `RepositorioOrden.guardar`.
   - Devuelve la `SenalPaso` que corresponda: `HayMasTrabajo(ordenGuardada)`
     si queda trabajo síncrono (lleva la instancia tal como la devolvió
     `guardar`, con su `version` real, para que el bucle de
     `ServicioContinuarOrden` encadene el siguiente paso sin recargar);
     `Aparcar(ventana)` si tu saga debe esperar un evento externo (ver el
     patrón de `SagaSecundaria2` más abajo); `Finalizada()` si el `Proceso`
     ya quedó `terminada()` (llama también a `orden.finalizar(ahora)` antes
     de guardar).
   - Si tu servicio mezcla I/O fuera de transacción con métodos
     `@Transactional`, necesita el mismo patrón `self` que
     `ServicioSagaSecundaria3`/`ServicioSagaPrincipal`: un campo `self` del
     propio tipo, inicializado a `this` en el constructor (para que los
     tests unitarios sin Spring funcionen sin más), con un
     `establecerSelf(...)` que la infraestructura sustituye por el proxy
     transaccional (ver el punto 4). Sin este indirection, una llamada
     interna (`this.aplicarX(...)`) saltaría el proxy de Spring y la
     anotación `@Transactional` se ignoraría silenciosamente.
   - Si tu servicio no intercala REST con la parte `@Transactional` (toda la
     operación pública es una única transacción, sin I/O externo en medio),
     no necesitas `self`: anota el método público directamente, como
     `ServicioCancelarTramitacion` o `ServicioSoporteOrdenes`. En cuanto haya
     una llamada REST/externa antes de la parte transaccional del mismo
     bean —como en `ServicioIniciarTramitacion`, que pide los datos de
     negocio antes de crear los agregados— hace falta `self`, aunque el
     servicio no sea un `ProcesadorOrden`.

## 3. Infraestructura (`infraestructure.<tucontexto>`)

Aquí sí hay Spring, JPA y el resto de frameworks — es la única capa donde
está permitido.

1. **Entidad JPA satélite + `JpaRepository`** (`persistencia`) — una tabla
   por tipo de orden, con **PK = el mismo `orden_id`** de la fila `orden`
   (relación 1:1, FK a `orden.orden_id`, **sin `ON DELETE CASCADE`**, ver
   `CLAUDE.md`). Copia `ProcesoSagaSecundaria3Entity` +
   `ProcesoSagaSecundaria3JpaRepository` como plantilla: solo necesitas un
   `save`/`findById` (heredados) y un `borrarPorIds` explícito con
   `@Modifying(clearAutomatically = true)` para la purga.
2. **El adaptador SPI** (`persistencia`, anotado `@Component`) — una clase
   que implementa `MapeadorProceso` **y** `DescriptorSoporteOrden` (ver
   `SoporteSagaSecundaria3`): `tipo()` devuelve tu `TIPO`; `estado()`/
   `guardarContexto()`/`rearmar()`/`borrarContexto()` traducen entre tu
   `Proceso` y tu entidad satélite; `pasoPendiente()`/
   `datosManualesObligatorios()`/`cancelable()` derivan de un `switch` sobre
   el `String estado` (el nombre del enum, no el enum en sí: esta SPI no
   depende de tus clases de dominio concretas más que para el propio mapeo).
3. **Los adaptadores de los puertos de salida** (`persistencia`, `eventos`,
   o donde corresponda) — implementan tus interfaces `PuertoXxx` con
   `RestTemplate`/`WebClient`/lo que uses; capturan el fallo del servicio
   remoto y lo traducen a `ExcepcionServicioExterno`.
4. **Registro de beans** (`ConfiguracionSagas` o tu propia
   `@Configuration`) — un método `@Bean` por `ProcesadorOrden` que construye
   el servicio de aplicación y, si usa `self`, le inyecta el proxy con un
   parámetro `@Lazy` del mismo tipo (ver `servicioSagaSecundaria3` en
   `ConfiguracionSagas` como plantilla exacta). Los adaptadores anotados
   `@Component`/`@Repository` (entidad, `MapeadorProceso`/
   `DescriptorSoporteOrden`, puertos de salida) se registran solos por
   classpath scanning: no hace falta declararlos aquí.
5. **La tabla SQL** (`order-manager/db/`) — un `.sql` nuevo con tu tabla
   satélite (copia `proceso_saga_secundaria3.sql`), añadido a la lista de
   `db/README.md` en el orden correcto (después de `orden.sql`, del que
   depende por FK).
6. Si un adaptador de entrada nuevo (REST, consumer de mensajería,
   `@Scheduled`) dispara tu saga, recuerda la regla de arquitectura del
   `CLAUDE.md`: nunca toca `RepositorioOrden` ni el agregado directamente,
   siempre pasa por un caso de uso de aplicación (ver
   `ConsumidorRespuestaSecundaria2` invocando
   `CasoUsoRegistrarRespuestaSecundaria2`, o `ControladorTramitaciones`
   invocando `CasoUsoIniciarTramitacion`).

## Patrones a reutilizar según la forma de tu saga

- **Un único paso síncrono, sin compensación** — copia `SagaSecundaria3` +
  `ServicioSagaSecundaria3` + `SoporteSagaSecundaria3` tal cual: es la
  plantilla más corta de las 4 sagas existentes.
- **Varios pasos síncronos encadenados, con compensación parcial** — copia
  `SagaPrincipal` + `ServicioSagaPrincipal`. Nota el patrón de cancelación:
  `Proceso.cancelar(...)` decide, según el estado en que se cancele, a qué
  estado de compensación saltar (`COMPENSAR_PASOn` más alto cuyo paso ya se
  ejecutó), y el `ProcesadorOrden` compensa un paso por pasada, en orden
  inverso, hasta `CANCELADA`. Si al alcanzar tu estado terminal quieres
  arrancar sagas hijas, hazlo en la misma transacción que finaliza la orden
  padre (`crearHijas(...)` en `ServicioSagaPrincipal.aplicarPasoNormal`):
  es la única excepción aceptada a "un agregado por transacción", porque
  solo MODIFICAS el agregado padre y CREAS los hijos, nunca los mutas.
- **Espera de un evento externo asíncrono** — copia `SagaSecundaria2` +
  `ServicioSagaSecundaria2`. El patrón: al enviar la solicitud, el
  `ProcesadorOrden` deja el `Proceso` en un estado "esperando" y devuelve
  `SenalPaso.Aparcar(ventana)` (`orden.aparcar(ventana, ahora)` libera el
  token y reprograma `proximoReintentoEn`); el evento externo llega por un
  adaptador de entrada propio (consumer Kafka, webhook...) que invoca un
  caso de uso dedicado (ver `CasoUsoRegistrarRespuestaSecundaria2`) — este
  caso de uso solo aplica la respuesta y despierta la orden
  (`orden.despertar(ahora)`), nunca decide el cierre operativo final; es el
  `ProcesadorOrden`, en su siguiente pasada normal, quien lo hace, para que
  exista un único punto que decide cuándo una orden queda `Finalizada`. Si
  la ventana de espera vence sin evento, el `ProcesadorOrden` concilia
  (pregunta activamente al servicio destino) en vez de dar la respuesta por
  perdida, porque el evento pudo perderse o estar aún en camino.

## Checklist antes de dar la saga por terminada

- [ ] `./gradlew check` en verde: `test` + `integrationTest` +
      `jacocoTestCoverageVerification` (100% instrucción y rama) +
      `ReglasArquitecturaTest` — en particular `ordermanagerNoDependeDeSagas`
      y `ordermanagerSinVocabularioDeSagas` (ninguna clase con "Saga" en el
      nombre dentro de `ordermanager`), `businessSinFrameworks` y
      `reglasDddDeJMolecules`.
- [ ] Tests unitarios (`src/test`) del `Proceso` (transiciones, invariantes)
      y del `ProcesadorOrden` con dobles en memoria; tests de integración
      (`src/integrationTest`) del adaptador `MapeadorProceso`/
      `DescriptorSoporteOrden` contra H2 modo Oracle.
- [ ] Diagramas actualizados (ver `CLAUDE.md`, sección "Diagramas: siempre
      sincronizados con el código"): como mínimo añade tu saga a
      13 (máquinas de estado) y crea/actualiza los diagramas de clases y de
      secuencia equivalentes a 15/16, 18-21 y 24-25 para tu tipo nuevo, y
      regenera los PNG (skill `puml-to-png`).
- [ ] `order-manager/docs/README.md` actualizado: añade tu SPI/tabla a las
      listas existentes si corresponde.

## Lo que nunca debes hacer

- Añadir o modificar código en `business.ordermanager` o
  `infraestructure.ordermanager` para dar cabida a tu saga: si crees que lo
  necesitas, la SPI se ha quedado corta y hay que ampliarla de forma
  genérica (afecta a todos los tipos de orden), no adaptarla a la tuya.
- Nombrar una clase de `ordermanager` con "Saga" en el nombre, ni importar
  nada de `business.sagas`/`infraestructure.sagas` desde `ordermanager`.
- Usar Spring, JPA, Jackson o Kafka en `business/**` (dominio o aplicación):
  toda esa infraestructura vive en `infraestructure/**`.
- `ON DELETE CASCADE` en tu tabla satélite ni en ninguna FK nueva: el
  borrado de hijas lo hace explícito, en la misma transacción,
  `MapeadorProceso.borrarContexto` (invocado por
  `AdaptadorRepositorioOrden.purgarPorExternalIds`).
- Que un adaptador de entrada nuevo llame directamente a `RepositorioOrden`
  o a cualquier puerto de salida sin pasar por un caso de uso de aplicación.
