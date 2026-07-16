package com.ejemplo.app.business.ordermanager.aplicacion.servicio;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.ejemplo.app.business.ordermanager.aplicacion.puerto.salida.RepositorioOrden;
import com.ejemplo.app.business.ordermanager.aplicacion.servicio.soporte.OrquestadorFalso;
import com.ejemplo.app.business.ordermanager.aplicacion.servicio.soporte.RepositorioOrdenEnMemoria;
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
import com.ejemplo.app.business.ordermanager.dominio.sagaprincipal.SagaPrincipalRoot;

/**
 * Bucle de ServicioContinuarSaga: reclamo del token con optimistic lock,
 * reintento con la escalera de backoff, concurrencia optimista ignorada y
 * lease vencido / takeover seguro entre pods (Fase 4 del refactor).
 */
class ServicioContinuarSagaTest {

    private static final Duration LEASE = Duration.ofMinutes(10);
    private static final PoliticaReintentos POLITICA = new PoliticaReintentos();

    private RepositorioOrdenEnMemoria repo;
    private UnidadDeTrabajoInmediata tx;

    @BeforeEach
    void init() {
        repo = new RepositorioOrdenEnMemoria();
        tx = new UnidadDeTrabajoInmediata();
    }

    private SagaId crearOrdenPrincipal() {
        var id = SagaId.nuevo();
        var saga = SagaPrincipalRoot.crear(id, ExternalId.de(UUID.randomUUID().toString()),
                new DatoNegocio3("v1", "v2"), new DatoNegocio2("v1", "v2"));
        repo.crear(OrdenRoot.nueva(saga, Instant.now()));
        return id;
    }

    private ServicioContinuarSaga servicio(OrquestadorSaga orquestador) {
        return new ServicioContinuarSaga(Map.of(TipoSaga.PRINCIPAL, orquestador), repo, tx, POLITICA, LEASE);
    }

    @Test
    void reintentoConEscalera_incrementaIntentosYProgramaElProximoSegunLaEscaleraTrasCadaFallo() {
        var id = crearOrdenPrincipal();
        var orquestador = new OrquestadorFalso(TipoSaga.PRINCIPAL,
                sagaId -> { throw new ExcepcionServicioExterno(MotivoFallo.timeout(), null); });
        var servicio = servicio(orquestador);

        var minutosEsperados = List.of(1, 3, 5);
        for (int minutos : minutosEsperados) {
            var antes = Instant.now();
            servicio.continuar(id, TipoSaga.PRINCIPAL);
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
        var orquestador = new OrquestadorFalso(TipoSaga.PRINCIPAL,
                sagaId -> { throw new ConcurrenciaOptimistaException(sagaId, 0); });
        var servicio = servicio(orquestador);

        assertThatCode(() -> servicio.continuar(id, TipoSaga.PRINCIPAL)).doesNotThrowAnyException();
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
        var orquestadorPodB = new OrquestadorFalso(TipoSaga.PRINCIPAL, sagaId -> {
            invocaciones.incrementAndGet();
            return new SenalPaso.Finalizada(ResultadoOrden.FINALIZADA_OK);
        });
        servicio(orquestadorPodB).continuar(id, TipoSaga.PRINCIPAL);

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
}
