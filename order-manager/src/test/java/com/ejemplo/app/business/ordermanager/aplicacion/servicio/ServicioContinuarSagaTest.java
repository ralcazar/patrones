package com.ejemplo.app.business.ordermanager.aplicacion.servicio;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.ejemplo.app.business.ordermanager.aplicacion.puerto.salida.RepositorioOrden;
import com.ejemplo.app.business.ordermanager.aplicacion.servicio.soporte.RepositorioOrdenEnMemoria;
import com.ejemplo.app.business.ordermanager.aplicacion.servicio.soporte.ServicioSagaFalso;
import com.ejemplo.app.business.ordermanager.aplicacion.servicio.soporte.UnidadDeTrabajoInmediata;
import com.ejemplo.app.business.ordermanager.dominio.comun.ConcurrenciaOptimistaException;
import com.ejemplo.app.business.ordermanager.dominio.comun.ExcepcionServicioExterno;
import com.ejemplo.app.business.ordermanager.dominio.comun.ExternalId;
import com.ejemplo.app.business.ordermanager.dominio.comun.MotivoFallo;
import com.ejemplo.app.business.ordermanager.dominio.comun.OrdenRoot;
import com.ejemplo.app.business.ordermanager.dominio.comun.PoliticaReintentos;
import com.ejemplo.app.business.ordermanager.dominio.comun.ResultadoOrden;
import com.ejemplo.app.business.ordermanager.dominio.comun.SagaId;
import com.ejemplo.app.business.ordermanager.dominio.comun.TipoSaga;
import com.ejemplo.app.business.ordermanager.dominio.sagaprincipal.DatoNegocio2;
import com.ejemplo.app.business.ordermanager.dominio.sagaprincipal.DatoNegocio3;
import com.ejemplo.app.business.ordermanager.dominio.sagaprincipal.SagaPrincipal;

/**
 * Bucle de ServicioContinuarSaga: reclamo del token con optimistic lock,
 * reintento con la escalera de backoff sobre la MISMA orden cargada (fix del
 * takeover seguro), concurrencia optimista ignorada, lease vencido / takeover
 * seguro entre pods y el pull de candidatas de los workers (continuarSiguiente
 * / hayTrabajoPendiente).
 */
class ServicioContinuarSagaTest {

    private static final Duration LEASE = Duration.ofMinutes(10);
    private static final PoliticaReintentos POLITICA = new PoliticaReintentos();
    private static final int LOTE = 16;

    private RepositorioOrdenEnMemoria repo;
    private UnidadDeTrabajoInmediata tx;

    @BeforeEach
    void init() {
        repo = new RepositorioOrdenEnMemoria();
        tx = new UnidadDeTrabajoInmediata();
    }

    private SagaId crearOrdenPrincipal() {
        var id = SagaId.nuevo();
        var saga = SagaPrincipal.crear(id, ExternalId.de(UUID.randomUUID().toString()),
                new DatoNegocio3("v1", "v2"), new DatoNegocio2("v1", "v2"));
        repo.crear(OrdenRoot.nueva(saga, Instant.now()));
        return id;
    }

    private ServicioContinuarSaga servicio(ServicioSaga servicioSaga) {
        return servicio(servicioSaga, repo);
    }

    private ServicioContinuarSaga servicio(ServicioSaga servicioSaga, RepositorioOrden repositorio) {
        return new ServicioContinuarSaga(Map.of(TipoSaga.PRINCIPAL, servicioSaga), repositorio, tx, POLITICA,
                LEASE, LOTE);
    }

    /** Simula que ya pasó el tiempo del reintento programado: la orden vuelve a ser candidata YA. */
    private void forzarCandidataAhora(SagaId id) {
        var orden = repo.cargar(id);
        orden.despertar(Instant.now());
        repo.guardar(orden);
    }

    @Test
    void reintentoConEscalera_incrementaIntentosYProgramaElProximoSegunLaEscaleraTrasCadaFallo() {
        var id = crearOrdenPrincipal();
        var servicioSaga = new ServicioSagaFalso(TipoSaga.PRINCIPAL,
                orden -> { throw new ExcepcionServicioExterno(MotivoFallo.timeout(), null); });
        var servicio = servicio(servicioSaga);

        var minutosEsperados = List.of(1, 3, 5);
        for (int minutos : minutosEsperados) {
            forzarCandidataAhora(id);
            var antes = Instant.now();
            assertThat(servicio.continuarSiguiente()).isTrue();
            var despues = Instant.now();
            var orden = repo.estadoActual(id);
            assertThat(orden.proximoReintentoEn())
                    .isBetween(antes.plus(Duration.ofMinutes(minutos)), despues.plus(Duration.ofMinutes(minutos)));
            assertThat(orden.tokenTrabajador()).isNull(); // se libera al programar el reintento
        }
        assertThat(repo.estadoActual(id).intentos()).isEqualTo(3);
    }

    @Test
    void concurrenciaOptimista_seIgnoraSilenciosamenteYElPodSeRetira() {
        var id = crearOrdenPrincipal();
        var servicioSaga = new ServicioSagaFalso(TipoSaga.PRINCIPAL,
                orden -> { throw new ConcurrenciaOptimistaException(orden.sagaId(), 0); });
        var servicio = servicio(servicioSaga);

        assertThatCode(() -> servicio.continuarSiguiente()).doesNotThrowAnyException();
    }

    @Test
    void leaseVencido_laOrdenReapareceComoCandidataYOtroPodLaReclama() {
        var id = crearOrdenPrincipal();
        var haceMucho = Instant.parse("2020-01-01T00:00:00Z");

        // Pod A reclama el token hace mucho y nunca vuelve a escribir (se considera muerto).
        var ordenA = repo.cargar(id);
        ordenA.asignarToken(UUID.randomUUID(), LEASE, haceMucho);
        repo.guardar(ordenA);
        assertThat(repo.estadoActual(id).version()).isEqualTo(1L);

        // Al vencer el lease, la orden vuelve a ser candidata del planificador.
        assertThat(repo.buscarEjecutables(Instant.now(), 10))
                .extracting(RepositorioOrden.CandidataOrden::sagaId)
                .contains(id);

        // Pod B la reclama de verdad y ejecuta.
        var invocaciones = new AtomicInteger();
        var servicioSagaPodB = new ServicioSagaFalso(TipoSaga.PRINCIPAL, orden -> {
            invocaciones.incrementAndGet();
            return new SenalPaso.Finalizada(ResultadoOrden.FINALIZADA_OK);
        });
        assertThat(servicio(servicioSagaPodB).continuarSiguiente()).isTrue();

        assertThat(invocaciones.get()).isEqualTo(1);
        assertThat(repo.estadoActual(id).version()).isGreaterThan(1L);
    }

    @Test
    void takeoverSeguro_elPodLentoQueVuelveFallaPorVersionAlGuardarYSeRetira() {
        var id = crearOrdenPrincipal();
        var haceMucho = Instant.parse("2020-01-01T00:00:00Z");

        // Pod A reclama (v0 -> v1) y se queda colgado con esa instantánea antes de escribir nada más.
        var ordenA = repo.cargar(id);
        ordenA.asignarToken(UUID.randomUUID(), LEASE, haceMucho);
        repo.guardar(ordenA);
        var instantaneaColgadaDePodA = repo.cargar(id); // versión 1, la que Pod A conserva mientras está colgado

        // El lease vence; Pod B reclama de verdad (v1 -> v2).
        var ordenB = repo.cargar(id);
        assertThat(ordenB.tieneTokenVigente(Instant.now())).isFalse();
        ordenB.asignarToken(UUID.randomUUID(), LEASE, Instant.now());
        repo.guardar(ordenB);
        assertThat(repo.estadoActual(id).version()).isEqualTo(2L);

        // Pod A por fin termina y trata de guardar su trabajo con la version obsoleta (1): falla y se retira.
        instantaneaColgadaDePodA.programarReintento(POLITICA, Instant.now());
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> repo.guardar(instantaneaColgadaDePodA))
                .isInstanceOf(ConcurrenciaOptimistaException.class);

        // El estado que ganó es el de Pod B, intacto.
        assertThat(repo.estadoActual(id).version()).isEqualTo(2L);
    }

    @Test
    void continuarSiguiente_sinCandidatas_devuelveFalse() {
        var servicioSaga = new ServicioSagaFalso(TipoSaga.PRINCIPAL,
                orden -> new SenalPaso.Finalizada(ResultadoOrden.FINALIZADA_OK));

        assertThat(servicio(servicioSaga).continuarSiguiente()).isFalse();
    }

    @Test
    void continuarSiguiente_conCandidataElegible_reclamaElTokenEjecutaLosPasosYDevuelveTrue() {
        var id = crearOrdenPrincipal();
        var invocaciones = new AtomicInteger();
        var servicioSaga = new ServicioSagaFalso(TipoSaga.PRINCIPAL, orden -> {
            invocaciones.incrementAndGet();
            return new SenalPaso.Aparcar(Duration.ofMinutes(5));
        });

        assertThat(servicio(servicioSaga).continuarSiguiente()).isTrue();

        assertThat(invocaciones.get()).isEqualTo(1);
        assertThat(repo.estadoActual(id).tokenTrabajador()).isNotNull(); // token reclamado
    }

    @Test
    void continuarSiguiente_laPrimeraCandidataPierdeElOptimisticLock_saltaALaSegundaYDevuelveTrue() {
        var idPerdida = crearOrdenPrincipal();
        crearOrdenPrincipal();
        var repoConCarrera = new RepositorioConCarrera(repo, List.of(idPerdida));
        var procesadas = new ArrayList<SagaId>();
        var servicioSaga = new ServicioSagaFalso(TipoSaga.PRINCIPAL, orden -> {
            procesadas.add(orden.sagaId());
            return new SenalPaso.Finalizada(ResultadoOrden.FINALIZADA_OK);
        });

        assertThat(servicio(servicioSaga, repoConCarrera).continuarSiguiente()).isTrue();

        assertThat(procesadas).hasSize(1).doesNotContain(idPerdida);
    }

    @Test
    void continuarSiguiente_todasLasCandidatasPierdenElReclamo_devuelveFalse() {
        var ids = List.of(crearOrdenPrincipal(), crearOrdenPrincipal());
        var repoConCarrera = new RepositorioConCarrera(repo, ids);
        var invocaciones = new AtomicInteger();
        var servicioSaga = new ServicioSagaFalso(TipoSaga.PRINCIPAL, orden -> {
            invocaciones.incrementAndGet();
            return new SenalPaso.Finalizada(ResultadoOrden.FINALIZADA_OK);
        });

        assertThat(servicio(servicioSaga, repoConCarrera).continuarSiguiente()).isFalse();
        assertThat(invocaciones.get()).isZero();
    }

    @Test
    void hayTrabajoPendiente_delegaEnElRepositorio() {
        var servicioSaga = new ServicioSagaFalso(TipoSaga.PRINCIPAL,
                orden -> new SenalPaso.Finalizada(ResultadoOrden.FINALIZADA_OK));
        var servicio = servicio(servicioSaga);

        assertThat(servicio.hayTrabajoPendiente()).isFalse();
        crearOrdenPrincipal();
        assertThat(servicio.hayTrabajoPendiente()).isTrue();
    }

    /**
     * Decorador del repo en memoria que simula la carrera con otro worker/pod:
     * a las órdenes marcadas les sube la versión justo después de cada
     * {@code cargar}, de modo que el {@code guardar} del reclamo pierde el
     * optimistic lock con {@link ConcurrenciaOptimistaException}.
     */
    private static final class RepositorioConCarrera implements RepositorioOrden {

        private final RepositorioOrdenEnMemoria delegado;
        private final List<SagaId> perdedoras;

        RepositorioConCarrera(RepositorioOrdenEnMemoria delegado, List<SagaId> perdedoras) {
            this.delegado = delegado;
            this.perdedoras = perdedoras;
        }

        @Override
        public OrdenRoot cargar(SagaId id) {
            var orden = delegado.cargar(id);
            if (perdedoras.contains(id)) {
                delegado.guardar(delegado.cargar(id)); // otro worker escribe entre medias: sube la versión
            }
            return orden;
        }

        @Override
        public void crear(OrdenRoot orden) { delegado.crear(orden); }

        @Override
        public void guardar(OrdenRoot orden) { delegado.guardar(orden); }

        @Override
        public List<CandidataOrden> buscarEjecutables(Instant ahora, int limite) {
            return delegado.buscarEjecutables(ahora, limite);
        }

        @Override
        public boolean hayEjecutables(Instant ahora) { return delegado.hayEjecutables(ahora); }

        @Override
        public long purgarFinalizadasAntesDe(Instant corte) { return delegado.purgarFinalizadasAntesDe(corte); }
    }
}
