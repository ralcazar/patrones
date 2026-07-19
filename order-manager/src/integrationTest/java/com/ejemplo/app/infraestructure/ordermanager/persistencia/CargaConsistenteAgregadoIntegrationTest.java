package com.ejemplo.app.infraestructure.ordermanager.persistencia;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Lazy;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;

import com.ejemplo.app.business.ordermanager.aplicacion.puerto.salida.RepositorioOrden;
import com.ejemplo.app.business.ordermanager.aplicacion.servicio.SenalPaso;
import com.ejemplo.app.business.ordermanager.aplicacion.servicio.ServicioContinuarOrden;
import com.ejemplo.app.business.ordermanager.dominio.ConcurrenciaOptimistaException;
import com.ejemplo.app.business.ordermanager.dominio.ExternalId;
import com.ejemplo.app.business.ordermanager.dominio.OrdenId;
import com.ejemplo.app.business.ordermanager.dominio.OrdenRoot;
import com.ejemplo.app.business.ordermanager.dominio.PoliticaReintentos;
import com.ejemplo.app.business.sagas.aplicacion.puerto.salida.PuertoConciliacionSecundaria2;
import com.ejemplo.app.business.sagas.aplicacion.puerto.salida.PuertoSagaSecundaria2;
import com.ejemplo.app.business.sagas.aplicacion.servicio.sagasecundaria2.ServicioSagaSecundaria2;
import com.ejemplo.app.business.sagas.dominio.comun.ContextoArranque;
import com.ejemplo.app.business.sagas.dominio.comun.RefPaso5;
import com.ejemplo.app.business.sagas.dominio.sagasecundaria2.EstadoSagaSecundaria2;
import com.ejemplo.app.business.sagas.dominio.sagasecundaria2.SagaSecundaria2;
import com.ejemplo.app.infraestructure.sagas.persistencia.ProcesoSagaPrincipalEntity;
import com.ejemplo.app.infraestructure.sagas.persistencia.ProcesoSagaSecundaria2JpaRepository;
import com.ejemplo.app.infraestructure.sagas.persistencia.SoporteSagaSecundaria2;
import com.ejemplo.app.testsoporte.InspectorSqlPausable;
import com.ejemplo.app.testsoporte.ObservadorEjecucionEnMemoria;

/**
 * FASE 1 del plan de refactor (Defecto A, lectura mixta/torn read): {@link
 * AdaptadorRepositorioOrden#cargar} lee el agregado en 3 SELECT separados
 * (proceso -&gt; satélite -&gt; orden), y solo la fila {@code orden} lleva
 * {@code version}. Bajo READ_COMMITTED, un commit ajeno entre el SELECT de
 * {@code proceso} y el de {@code orden} produce una lectura mixta: FSM de
 * negocio vieja + fila de ejecución fresca. Como la version leída es la
 * fresca, el guardado posterior pasa el candado optimista y el pod re-ejecuta
 * un paso ya hecho (aquí, una llamada REST duplicada a {@code
 * PuertoSagaSecundaria2#solicitar}).
 *
 * Reproduce la intercalación con hilos reales y un {@link InspectorSqlPausable}
 * que pausa SOLO al hilo B, justo antes de su SELECT de la tabla {@code orden}
 * (después de que ya haya leído {@code proceso}), hasta que el hilo A confirma
 * que ya comiteó su paso completo (reclamo + solicitar + aparcar).
 *
 * Se arregla en la fase 2 (fusión de tablas orden+proceso en una fila
 * atómica, fuera del alcance de este test): las aserciones de este test son
 * deliberadamente sobre comportamiento observable (número de invocaciones a
 * {@code solicitar}, estado final de la orden), no sobre las sentencias SQL
 * internas, para que sigan siendo válidas tal cual tras esa fusión (el punto
 * de pausa del inspector simplemente dejará de alcanzarse).
 */
@SpringBootTest(classes = CargaConsistenteAgregadoIntegrationTest.ContextoTest.class)
@ActiveProfiles("test")
@TestPropertySource(properties =
        "spring.jpa.properties.hibernate.session_factory.statement_inspector="
                + "com.ejemplo.app.testsoporte.InspectorSqlPausable")
class CargaConsistenteAgregadoIntegrationTest {

    @Autowired
    private RepositorioOrden repo;

    @Autowired
    private ServicioContinuarOrden servicioContinuarOrden;

    @Autowired
    private ServicioSagaSecundaria2 procesadorSecundaria2;

    @Autowired
    private PuertoSagaSecundaria2 puertoSagaSecundaria2;

    @Autowired
    private PuertoConciliacionSecundaria2 puertoConciliacionSecundaria2;

    @Autowired
    private OrdenJpaRepository ordenJpaRepository;

    @Autowired
    private ProcesoSagaSecundaria2JpaRepository procesoSagaSecundaria2JpaRepository;

    @AfterEach
    void limpiarBaseDeDatosYHook() {
        InspectorSqlPausable.hook = InspectorSqlPausable.NINGUNO;
        // Tras la fusión de orden+proceso (fase 2): la satélite (hija, FK a orden) se
        // borra ANTES que orden (ahora el padre).
        procesoSagaSecundaria2JpaRepository.deleteAll();
        ordenJpaRepository.deleteAll();
    }

    private OrdenId crearOrdenSecundaria2Ejecutable() {
        var id = OrdenId.nuevo();
        var ctx = new ContextoArranque.ArranqueSecundaria2(
                ExternalId.de(UUID.randomUUID().toString()), new RefPaso5("ref5"));
        // proximoReintentoEn = ahora: ya ejecutable de inmediato (ver OrdenRoot.nueva).
        repo.crear(OrdenRoot.nueva(SagaSecundaria2.crear(id, ctx), Instant.now()));
        return id;
    }

    /** Mismo recorrido que el paso privado ServicioContinuarOrden.reclamarYEjecutar, para orquestar los 2 hilos desde el test. */
    private Optional<SenalPaso> intentarReclamarYEjecutarPaso(OrdenId id) {
        Optional<OrdenRoot> reclamada;
        try {
            reclamada = servicioContinuarOrden.reclamarToken(id);
        } catch (ConcurrenciaOptimistaException e) {
            return Optional.empty(); // reclamo perdido por colisión optimista: retirada silenciosa
        }
        return reclamada.map(procesadorSecundaria2::ejecutarPaso);
    }

    private static void esperarComoMaximo(CountDownLatch latch, Duration timeout) {
        try {
            latch.await(timeout.toMillis(), TimeUnit.MILLISECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    @Test
    void dosPodsReclamanLaMismaOrdenSecundaria2ConLecturaMixta_soloInvocaSolicitarUnaVez() throws Exception {
        // Por si en el futuro (tras la fase 2) el paso INICIAL->conciliar llegase a
        // alcanzarse: que no reviente por NPE en un mock sin configurar.
        when(puertoConciliacionSecundaria2.consultar(any(), any()))
                .thenReturn(new PuertoConciliacionSecundaria2.Resultado.SinResultado());

        var id = crearOrdenSecundaria2Ejecutable();
        var hiloB = new AtomicReference<Thread>();
        var procesoYaLeidoPorHiloB = new CountDownLatch(1);
        var hiloAHizoCommit = new CountDownLatch(1);

        // Pausa SOLO al hilo B (por identidad de Thread), y solo en su SELECT de la
        // tabla orden (el ÚLTIMO de los 3 SELECT de cargar(): proceso -> satélite ->
        // orden, ver AdaptadorRepositorioOrden.cargar). El hilo A nunca coincide con
        // "hiloB.get()", así que sus propias sentencias nunca se pausan.
        InspectorSqlPausable.hook = sql -> {
            if (Thread.currentThread().equals(hiloB.get()) && sql.toLowerCase(Locale.ROOT).contains("from orden")) {
                procesoYaLeidoPorHiloB.countDown();
                esperarComoMaximo(hiloAHizoCommit, Duration.ofSeconds(5));
            }
        };

        var executor = Executors.newSingleThreadExecutor();
        try {
            Future<Optional<SenalPaso>> futuroB = executor.submit(() -> {
                hiloB.set(Thread.currentThread());
                return intentarReclamarYEjecutarPaso(id);
            });

            // Le damos al hilo B una oportunidad acotada de llegar a la pausa (ya leyó
            // proceso, a punto de leer orden) antes de que el hilo A actúe. Si el punto
            // de pausa ya no existiera (fix de fase 2, tablas fusionadas), este await
            // simplemente agota el timeout sin bloquear el test.
            procesoYaLeidoPorHiloB.await(2, TimeUnit.SECONDS);

            // Hilo A: el propio hilo del test reclama la orden y ejecuta su paso
            // COMPLETO (solicitar + aparcar), comiteando antes de liberar al hilo B.
            var senalA = intentarReclamarYEjecutarPaso(id);
            hiloAHizoCommit.countDown();

            var senalB = futuroB.get(10, TimeUnit.SECONDS);

            assertThat(senalA).as("el hilo A gana el reclamo y ejecuta su paso completo").isPresent();

            // Aserción principal (HOY en rojo): con la lectura mixta, el hilo B rehidrata
            // una FSM vieja (INICIAL) con una version fresca, el candado optimista no lo
            // detecta, y vuelve a invocar solicitar(): 2 llamadas en vez de 1.
            verify(puertoSagaSecundaria2, times(1)).solicitar(eq(id), any());

            // NOTA (decisión del coordinador, fase 2): esta aserción originalmente exigía
            // isEmpty() — el plan asumía que la fusión también hacía que el hilo B se
            // retirase. No es así: tras la fusión, el SELECT de B (reanudado después del
            // commit de A) lee una foto fresca y consistente, con la version ya al día,
            // así que su propio guardado NO colisiona (no hay torn read: Defecto A ya
            // arreglado, ver verify(times(1)) arriba). Pero reclamarToken todavía no
            // re-comprueba si la orden está en turno de ejecución (proximoReintentoEn
            // <= ahora) sobre esa foto fresca — es exactamente el Defecto B, que el plan
            // dice explícitamente que "ocurre incluso con lectura consistente" y que NO
            // arregla la fusión, sino el re-check de la fase 3. Por eso HOY el hilo B
            // reclama de nuevo y re-aparca (sin volver a invocar solicitar). La fase 3
            // REFORZARÁ esta aserción a isEmpty() cuando cierre ese hueco.
            assertThat(senalB)
                    .as("hoy (antes de la fase 3) el hilo B reclama y re-aparca, pero nunca re-solicita")
                    .isNotEmpty();

            var ordenFinal = repo.cargar(id);
            assertThat(ordenFinal.estaViva()).as("la orden queda aparcada, no en un estado inconsistente").isTrue();
            assertThat(((SagaSecundaria2) ordenFinal.proceso()).estado())
                    .as("la FSM de negocio queda coherente con el paso ya ejecutado")
                    .isEqualTo(EstadoSagaSecundaria2.ESPERANDO_RESPUESTA);
        } finally {
            InspectorSqlPausable.hook = InspectorSqlPausable.NINGUNO;
            executor.shutdownNow();
        }
    }

    /**
     * Contexto Spring mínimo: solo lo que hace falta para reclamar/ejecutar un
     * paso de SagaSecundaria2 contra JPA/H2 real, con el StatementInspector
     * registrado como propiedad de Hibernate (ver {@code @TestPropertySource}).
     */
    @Configuration
    @EnableAutoConfiguration
    @EntityScan(basePackageClasses = {OrdenEntity.class, ProcesoSagaPrincipalEntity.class})
    @EnableJpaRepositories(basePackageClasses = {OrdenEntity.class, ProcesoSagaPrincipalEntity.class})
    static class ContextoTest {

        // Único bean para el puerto genérico RepositorioOrden: si además se publicara
        // como AdaptadorRepositorioOrden (tipo concreto) en otro @Bean, Spring vería 2
        // candidatos para cualquier @Bean que pida el tipo RepositorioOrden (ambigüedad).
        @Bean
        RepositorioOrden repositorioOrden(OrdenJpaRepository ordenes,
                ProcesoSagaSecundaria2JpaRepository repoSecundaria2) {
            return new AdaptadorRepositorioOrden(ordenes, List.of(new SoporteSagaSecundaria2(repoSecundaria2)));
        }

        @Bean
        PuertoSagaSecundaria2 puertoSagaSecundaria2() {
            return mock(PuertoSagaSecundaria2.class);
        }

        @Bean
        PuertoConciliacionSecundaria2 puertoConciliacionSecundaria2() {
            return mock(PuertoConciliacionSecundaria2.class);
        }

        @Bean
        ServicioSagaSecundaria2 servicioSagaSecundaria2(RepositorioOrden repo, PuertoSagaSecundaria2 puerto,
                PuertoConciliacionSecundaria2 conciliacion, @Lazy ServicioSagaSecundaria2 self) {
            var servicio = new ServicioSagaSecundaria2(repo, puerto, conciliacion);
            servicio.establecerSelf(self);
            return servicio;
        }

        @Bean
        ServicioContinuarOrden servicioContinuarOrden(RepositorioOrden repo, ServicioSagaSecundaria2 procesador,
                @Lazy ServicioContinuarOrden self) {
            var servicio = new ServicioContinuarOrden(Map.of(SagaSecundaria2.TIPO, procesador), repo,
                    new PoliticaReintentos(), Duration.ofMinutes(10), 16, new ObservadorEjecucionEnMemoria());
            servicio.establecerSelf(self);
            return servicio;
        }
    }
}
