package com.ejemplo.app.business.ordermanager.aplicacion.servicio;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;

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
import com.ejemplo.app.testsoporte.RepositorioOrdenEnMemoria;
import com.ejemplo.app.business.ordermanager.aplicacion.servicio.soporte.ProcesadorOrdenFalso;
import com.ejemplo.app.business.ordermanager.dominio.ConcurrenciaOptimistaException;
import com.ejemplo.app.business.ordermanager.dominio.ExcepcionServicioExterno;
import com.ejemplo.app.business.ordermanager.dominio.ExternalId;
import com.ejemplo.app.business.ordermanager.dominio.MotivoFallo;
import com.ejemplo.app.business.ordermanager.dominio.OrdenRoot;
import com.ejemplo.app.business.ordermanager.dominio.PoliticaReintentos;
import com.ejemplo.app.business.ordermanager.dominio.ProcesoFalso;
import com.ejemplo.app.business.ordermanager.dominio.OrdenId;

/**
 * Bucle de ServicioContinuarOrden: reclamo del token con optimistic lock,
 * reintento con la escalera de backoff sobre la MISMA orden cargada (fix del
 * takeover seguro), concurrencia optimista ignorada, lease vencido / takeover
 * seguro entre pods y el pull de candidatas de los workers (continuarSiguiente
 * / hayTrabajoPendiente).
 */
class ServicioContinuarOrdenTest {

    private static final Duration LEASE = Duration.ofMinutes(10);
    private static final PoliticaReintentos POLITICA = new PoliticaReintentos();
    private static final int LOTE = 16;

    private RepositorioOrdenEnMemoria repo;

    @BeforeEach
    void init() {
        repo = new RepositorioOrdenEnMemoria();
    }

    private OrdenId crearOrdenFalsa() {
        var id = OrdenId.nuevo();
        var proceso = ProcesoFalso.crear(id, ExternalId.de(UUID.randomUUID().toString()));
        repo.crear(OrdenRoot.nueva(proceso, Instant.now()));
        return id;
    }

    private ServicioContinuarOrden servicio(ProcesadorOrden procesador) {
        return servicio(procesador, repo);
    }

    private ServicioContinuarOrden servicio(ProcesadorOrden procesador, RepositorioOrden repositorio) {
        return new ServicioContinuarOrden(Map.of(ProcesoFalso.TIPO, procesador), repositorio, POLITICA,
                LEASE, LOTE);
    }

    /** Simula que ya pasó el tiempo del reintento programado: la orden vuelve a ser candidata YA. */
    private void forzarCandidataAhora(OrdenId id) {
        var orden = repo.cargar(id);
        orden.despertar(Instant.now());
        repo.guardar(orden);
    }

    @Test
    void reintentoConEscalera_incrementaIntentosYProgramaElProximoSegunLaEscaleraTrasCadaFallo() {
        var id = crearOrdenFalsa();
        var procesador = new ProcesadorOrdenFalso(ProcesoFalso.TIPO,
                orden -> { throw new ExcepcionServicioExterno(MotivoFallo.timeout(), null); });
        var servicio = servicio(procesador);

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
        var id = crearOrdenFalsa();
        var procesador = new ProcesadorOrdenFalso(ProcesoFalso.TIPO,
                orden -> { throw new ConcurrenciaOptimistaException(orden.id(), 0); });
        var servicio = servicio(procesador);

        assertThatCode(() -> servicio.continuarSiguiente()).doesNotThrowAnyException();
    }

    @Test
    void leaseVencido_laOrdenReapareceComoCandidataYOtroPodLaReclama() {
        var id = crearOrdenFalsa();
        var haceMucho = Instant.parse("2020-01-01T00:00:00Z");

        // Pod A reclama el token hace mucho y nunca vuelve a escribir (se considera muerto).
        var ordenA = repo.cargar(id);
        ordenA.asignarToken(UUID.randomUUID(), LEASE, haceMucho);
        repo.guardar(ordenA);
        assertThat(repo.estadoActual(id).version()).isEqualTo(1L);

        // Al vencer el lease, la orden vuelve a ser candidata del planificador.
        assertThat(repo.buscarEjecutables(Instant.now(), 10))
                .extracting(RepositorioOrden.CandidataOrden::ordenId)
                .contains(id);

        // Pod B la reclama de verdad y ejecuta.
        var invocaciones = new AtomicInteger();
        var procesadorPodB = new ProcesadorOrdenFalso(ProcesoFalso.TIPO, orden -> {
            invocaciones.incrementAndGet();
            return new SenalPaso.Finalizada();
        });
        assertThat(servicio(procesadorPodB).continuarSiguiente()).isTrue();

        assertThat(invocaciones.get()).isEqualTo(1);
        assertThat(repo.estadoActual(id).version()).isGreaterThan(1L);
    }

    @Test
    void takeoverSeguro_elPodLentoQueVuelveFallaPorVersionAlGuardarYSeRetira() {
        var id = crearOrdenFalsa();
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
        var procesador = new ProcesadorOrdenFalso(ProcesoFalso.TIPO,
                orden -> new SenalPaso.Finalizada());

        assertThat(servicio(procesador).continuarSiguiente()).isFalse();
    }

    @Test
    void continuarSiguiente_conCandidataElegible_reclamaElTokenEjecutaLosPasosYDevuelveTrue() {
        var id = crearOrdenFalsa();
        var invocaciones = new AtomicInteger();
        var procesador = new ProcesadorOrdenFalso(ProcesoFalso.TIPO, orden -> {
            invocaciones.incrementAndGet();
            return new SenalPaso.Aparcar(Duration.ofMinutes(5));
        });

        assertThat(servicio(procesador).continuarSiguiente()).isTrue();

        assertThat(invocaciones.get()).isEqualTo(1);
        assertThat(repo.estadoActual(id).tokenTrabajador()).isNotNull(); // token reclamado
    }

    @Test
    void continuarSiguiente_laPrimeraCandidataPierdeElOptimisticLock_saltaALaSegundaYDevuelveTrue() {
        var idPerdida = crearOrdenFalsa();
        crearOrdenFalsa();
        var repoConCarrera = new RepositorioConCarrera(repo, List.of(idPerdida));
        var procesadas = new ArrayList<OrdenId>();
        var procesador = new ProcesadorOrdenFalso(ProcesoFalso.TIPO, orden -> {
            procesadas.add(orden.id());
            return new SenalPaso.Finalizada();
        });

        assertThat(servicio(procesador, repoConCarrera).continuarSiguiente()).isTrue();

        assertThat(procesadas).hasSize(1).doesNotContain(idPerdida);
    }

    @Test
    void continuarSiguiente_todasLasCandidatasPierdenElReclamo_devuelveFalse() {
        var ids = List.of(crearOrdenFalsa(), crearOrdenFalsa());
        var repoConCarrera = new RepositorioConCarrera(repo, ids);
        var invocaciones = new AtomicInteger();
        var procesador = new ProcesadorOrdenFalso(ProcesoFalso.TIPO, orden -> {
            invocaciones.incrementAndGet();
            return new SenalPaso.Finalizada();
        });

        assertThat(servicio(procesador, repoConCarrera).continuarSiguiente()).isFalse();
        assertThat(invocaciones.get()).isZero();
    }

    @Test
    void hayTrabajoPendiente_delegaEnElRepositorio() {
        var procesador = new ProcesadorOrdenFalso(ProcesoFalso.TIPO,
                orden -> new SenalPaso.Finalizada());
        var servicio = servicio(procesador);

        assertThat(servicio.hayTrabajoPendiente()).isFalse();
        crearOrdenFalsa();
        assertThat(servicio.hayTrabajoPendiente()).isTrue();
    }

    @Test
    void reclamarToken_devuelveOptionalVacioSiLaOrdenYaEstaFinalizada_ramaDeEstadoNoDeExcepcion() {
        var id = crearOrdenFalsa();
        var orden = repo.cargar(id);
        orden.finalizar(Instant.now());
        repo.guardar(orden);
        var procesador = new ProcesadorOrdenFalso(ProcesoFalso.TIPO,
                o -> { throw new IllegalStateException("no debería ejecutarse: la orden ya está finalizada"); });

        assertThat(servicio(procesador).reclamarToken(id)).isEmpty();

        assertThat(repo.estadoActual(id).tokenTrabajador()).isNull();
    }

    @Test
    void reclamarToken_devuelveOptionalVacioSiYaTieneTokenVigente_ramaDeEstadoNoDeExcepcion() {
        var id = crearOrdenFalsa();
        var orden = repo.cargar(id);
        orden.asignarToken(UUID.randomUUID(), LEASE, Instant.now());
        repo.guardar(orden);
        var tokenPrevio = repo.estadoActual(id).tokenTrabajador();
        var procesador = new ProcesadorOrdenFalso(ProcesoFalso.TIPO,
                o -> { throw new IllegalStateException("no debería ejecutarse: el token sigue vigente"); });

        assertThat(servicio(procesador).reclamarToken(id)).isEmpty();

        assertThat(repo.estadoActual(id).tokenTrabajador()).isEqualTo(tokenPrevio);
    }

    @Test
    void continuarSiguiente_primeraCandidataYaTieneTokenVigenteAlReclamar_saltaALaSegundaSinExcepcion() {
        var idYaTomada = crearOrdenFalsa();
        var idLibre = crearOrdenFalsa();
        var repoConCarrera = new RepositorioConTokenYaAsignadoAlReclamar(repo, idYaTomada, LEASE);
        var procesadas = new ArrayList<OrdenId>();
        var procesador = new ProcesadorOrdenFalso(ProcesoFalso.TIPO, orden -> {
            procesadas.add(orden.id());
            return new SenalPaso.Finalizada();
        });

        assertThat(servicio(procesador, repoConCarrera).continuarSiguiente()).isTrue();

        assertThat(procesadas).containsExactly(idLibre);
    }

    @Test
    void reclamarYEjecutar_sinProcesadorRegistradoParaElTipo_lanzaIllegalStateException() {
        crearOrdenFalsa();
        var servicioSinProcesadores = new ServicioContinuarOrden(Map.of(), repo, POLITICA, LEASE, LOTE);

        assertThatThrownBy(servicioSinProcesadores::continuarSiguiente)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining(ProcesoFalso.TIPO.valor());
    }

    @Test
    void ejecutarPasoFallaYProgramarReintentoPierdeLaVersion_laExcepcionSePropaga() {
        // Fija el comportamiento ACTUAL: a diferencia del reclamo (que se traga el
        // conflicto), un conflicto en programarReintento SÍ se propaga sin capturar.
        var id = crearOrdenFalsa();
        var repoConCarrera = new RepositorioConCarreraTrasElReclamo(repo, id);
        var procesador = new ProcesadorOrdenFalso(ProcesoFalso.TIPO,
                orden -> { throw new ExcepcionServicioExterno(MotivoFallo.timeout(), null); });

        assertThatThrownBy(() -> servicio(procesador, repoConCarrera).continuarSiguiente())
                .isInstanceOf(ConcurrenciaOptimistaException.class);
    }

    @Test
    void continuarSiguiente_conHayMasTrabajoSeguidoDeFinalizada_iteraDosVecesSinNingunaRecargaMasAlaDeLaInicialDelReclamo() {
        var id = crearOrdenFalsa();
        var repoContador = new RepositorioContadorDeCargas(repo, id);
        var invocaciones = new AtomicInteger();
        var idsVistos = new ArrayList<OrdenId>();
        var procesador = new ProcesadorOrdenFalso(ProcesoFalso.TIPO, orden -> {
            idsVistos.add(orden.id());
            if (invocaciones.incrementAndGet() == 1) {
                var ordenGuardada = repoContador.guardar(orden);
                return new SenalPaso.HayMasTrabajo(ordenGuardada);
            }
            return new SenalPaso.Finalizada();
        });

        assertThat(servicio(procesador, repoContador).continuarSiguiente()).isTrue();

        assertThat(invocaciones.get()).isEqualTo(2);
        assertThat(idsVistos).containsExactly(id, id); // ambos pasos operan sobre la misma orden
        // Una sola carga en todo el bucle, la de reclamarToken: el primer paso
        // reutiliza la instancia que devuelve el guardado del reclamo, y el
        // segundo paso reutiliza la que trae la señal HayMasTrabajo (la que
        // devolvió el guardado del propio procesador). Cero recargas reales,
        // sea cual sea el número de pasos.
        assertThat(repoContador.cargas()).isEqualTo(1);
    }

    @Test
    void establecerSelf_sustituyeElProxyUsadoParaReclamarTokenYProgramarReintento() {
        var id = crearOrdenFalsa();
        var procesador = new ProcesadorOrdenFalso(ProcesoFalso.TIPO,
                orden -> new SenalPaso.Finalizada());
        var servicio = servicio(procesador);
        var proxy = spy(servicio);
        servicio.establecerSelf(proxy);

        assertThat(servicio.continuarSiguiente()).isTrue();

        verify(proxy).reclamarToken(id);
    }

    /**
     * Decorador que, en el {@code cargar} usado por el reclamo (self.reclamarToken),
     * devuelve una copia con un token ya vigente: simula que otro actor la reclamó
     * justo antes de que este pod la intentara, sin que buscarEjecutables lo reflejara todavía.
     */
    private static final class RepositorioConTokenYaAsignadoAlReclamar implements RepositorioOrden {

        private final RepositorioOrdenEnMemoria delegado;
        private final OrdenId objetivo;
        private final Duration lease;

        RepositorioConTokenYaAsignadoAlReclamar(RepositorioOrdenEnMemoria delegado, OrdenId objetivo, Duration lease) {
            this.delegado = delegado;
            this.objetivo = objetivo;
            this.lease = lease;
        }

        @Override
        public OrdenRoot cargar(OrdenId id) {
            var orden = delegado.cargar(id);
            if (id.equals(objetivo)) {
                orden.asignarToken(UUID.randomUUID(), lease, Instant.now());
            }
            return orden;
        }

        @Override
        public void crear(OrdenRoot orden) { delegado.crear(orden); }

        @Override
        public OrdenRoot guardar(OrdenRoot orden) { return delegado.guardar(orden); }

        @Override
        public List<CandidataOrden> buscarEjecutables(Instant ahora, int limite) {
            return delegado.buscarEjecutables(ahora, limite);
        }

        @Override
        public boolean hayEjecutables(Instant ahora) { return delegado.hayEjecutables(ahora); }

        @Override
        public long purgarFinalizadasAntesDe(Instant corte) { return delegado.purgarFinalizadasAntesDe(corte); }
    }

    /**
     * Decorador que, justo tras el guardado del reclamo (1er {@code guardar}
     * para el objetivo, dentro de {@code self.reclamarToken}), simula que otro
     * actor escribe entre medias: la instancia que {@code reclamarToken}
     * devuelve para ejecutar el primer paso queda con la versión desactualizada
     * (ya no por la conversión sin recarga, sino por esta escritura ajena real),
     * de forma que el guardado del reintento posterior falla por optimistic lock.
     */
    private static final class RepositorioConCarreraTrasElReclamo implements RepositorioOrden {

        private final RepositorioOrdenEnMemoria delegado;
        private final OrdenId objetivo;
        private int llamadasGuardar = 0;

        RepositorioConCarreraTrasElReclamo(RepositorioOrdenEnMemoria delegado, OrdenId objetivo) {
            this.delegado = delegado;
            this.objetivo = objetivo;
        }

        @Override
        public OrdenRoot cargar(OrdenId id) { return delegado.cargar(id); }

        @Override
        public void crear(OrdenRoot orden) { delegado.crear(orden); }

        @Override
        public OrdenRoot guardar(OrdenRoot orden) {
            var guardada = delegado.guardar(orden);
            if (orden.id().equals(objetivo)) {
                llamadasGuardar++;
                if (llamadasGuardar == 1) {
                    delegado.guardar(delegado.cargar(objetivo)); // otro actor escribe entre medias: sube la versión
                }
            }
            return guardada;
        }

        @Override
        public List<CandidataOrden> buscarEjecutables(Instant ahora, int limite) {
            return delegado.buscarEjecutables(ahora, limite);
        }

        @Override
        public boolean hayEjecutables(Instant ahora) { return delegado.hayEjecutables(ahora); }

        @Override
        public long purgarFinalizadasAntesDe(Instant corte) { return delegado.purgarFinalizadasAntesDe(corte); }
    }

    /**
     * Decorador que cuenta las llamadas a {@code cargar} para un id objetivo:
     * sirve para verificar en el test cuántas cargas reales hace
     * {@code reclamarYEjecutar} (ver {@code ServicioContinuarOrden}).
     */
    private static final class RepositorioContadorDeCargas implements RepositorioOrden {

        private final RepositorioOrdenEnMemoria delegado;
        private final OrdenId objetivo;
        private int cargas = 0;

        RepositorioContadorDeCargas(RepositorioOrdenEnMemoria delegado, OrdenId objetivo) {
            this.delegado = delegado;
            this.objetivo = objetivo;
        }

        int cargas() { return cargas; }

        @Override
        public OrdenRoot cargar(OrdenId id) {
            if (id.equals(objetivo)) {
                cargas++;
            }
            return delegado.cargar(id);
        }

        @Override
        public void crear(OrdenRoot orden) { delegado.crear(orden); }

        @Override
        public OrdenRoot guardar(OrdenRoot orden) { return delegado.guardar(orden); }

        @Override
        public List<CandidataOrden> buscarEjecutables(Instant ahora, int limite) {
            return delegado.buscarEjecutables(ahora, limite);
        }

        @Override
        public boolean hayEjecutables(Instant ahora) { return delegado.hayEjecutables(ahora); }

        @Override
        public long purgarFinalizadasAntesDe(Instant corte) { return delegado.purgarFinalizadasAntesDe(corte); }
    }

    /**
     * Decorador del repo en memoria que simula la carrera con otro worker/pod:
     * a las órdenes marcadas les sube la versión justo después de cada
     * {@code cargar}, de modo que el {@code guardar} del reclamo pierde el
     * optimistic lock con {@link ConcurrenciaOptimistaException}.
     */
    private static final class RepositorioConCarrera implements RepositorioOrden {

        private final RepositorioOrdenEnMemoria delegado;
        private final List<OrdenId> perdedoras;

        RepositorioConCarrera(RepositorioOrdenEnMemoria delegado, List<OrdenId> perdedoras) {
            this.delegado = delegado;
            this.perdedoras = perdedoras;
        }

        @Override
        public OrdenRoot cargar(OrdenId id) {
            var orden = delegado.cargar(id);
            if (perdedoras.contains(id)) {
                delegado.guardar(delegado.cargar(id)); // otro worker escribe entre medias: sube la versión
            }
            return orden;
        }

        @Override
        public void crear(OrdenRoot orden) { delegado.crear(orden); }

        @Override
        public OrdenRoot guardar(OrdenRoot orden) { return delegado.guardar(orden); }

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
