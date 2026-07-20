package com.ejemplo.app.infraestructure.ordermanager.persistencia;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;

import com.ejemplo.app.business.ordermanager.aplicacion.puerto.entrada.CasoUsoConsultarOrdenesSoporte.FiltroOrdenes;
import com.ejemplo.app.business.ordermanager.aplicacion.puerto.salida.RepositorioOrden;
import com.ejemplo.app.business.ordermanager.aplicacion.puerto.salida.RepositorioOrden.CandidataOrden;
import com.ejemplo.app.business.ordermanager.dominio.ConcurrenciaOptimistaException;
import com.ejemplo.app.business.ordermanager.dominio.DetalleError;
import com.ejemplo.app.business.ordermanager.dominio.ExternalId;
import com.ejemplo.app.business.ordermanager.dominio.OrdenId;
import com.ejemplo.app.business.ordermanager.dominio.OrdenRoot;
import com.ejemplo.app.business.ordermanager.dominio.TipoOrden;
import com.ejemplo.app.business.ordermanager.dominio.UsuarioSoporte;
import com.ejemplo.app.business.sagas.dominio.comun.ContextoArranque;
import com.ejemplo.app.business.sagas.dominio.datosnegocio.DatosNegocioId;
import com.ejemplo.app.business.sagas.dominio.sagaprincipal.SagaPrincipal;
import com.ejemplo.app.business.sagas.dominio.comun.RefPaso1;
import com.ejemplo.app.business.sagas.dominio.comun.RefPaso5;
import com.ejemplo.app.business.sagas.dominio.sagasecundaria1.SagaSecundaria1;
import com.ejemplo.app.business.sagas.dominio.sagasecundaria2.SagaSecundaria2;
import com.ejemplo.app.infraestructure.sagas.sagaprincipal.persistencia.ProcesoSagaPrincipalEntity;
import com.ejemplo.app.infraestructure.sagas.sagaprincipal.persistencia.ProcesoSagaPrincipalJpaRepository;
import com.ejemplo.app.infraestructure.sagas.sagasecundaria1.persistencia.ProcesoSagaSecundaria1Entity;
import com.ejemplo.app.infraestructure.sagas.sagasecundaria1.persistencia.ProcesoSagaSecundaria1JpaRepository;
import com.ejemplo.app.infraestructure.sagas.sagasecundaria2.persistencia.ProcesoSagaSecundaria2Entity;
import com.ejemplo.app.infraestructure.sagas.sagasecundaria2.persistencia.ProcesoSagaSecundaria2JpaRepository;
import com.ejemplo.app.infraestructure.sagas.sagasecundaria3.persistencia.ProcesoSagaSecundaria3Entity;
import com.ejemplo.app.infraestructure.sagas.sagasecundaria3.persistencia.ProcesoSagaSecundaria3JpaRepository;
import com.ejemplo.app.infraestructure.sagas.sagaprincipal.persistencia.SoporteSagaPrincipal;
import com.ejemplo.app.infraestructure.sagas.sagasecundaria1.persistencia.SoporteSagaSecundaria1;
import com.ejemplo.app.infraestructure.sagas.sagasecundaria2.persistencia.SoporteSagaSecundaria2;
import com.ejemplo.app.infraestructure.sagas.sagasecundaria3.persistencia.SoporteSagaSecundaria3;

/**
 * Adaptadores JPA reales sobre H2 en memoria (modo Oracle, ver
 * application-test.yml): ejercita {@link AdaptadorRepositorioOrden},
 * {@link AdaptadorConsultaOrdenesSoporte} y {@link AdaptadorOrdenesTicketPendiente},
 * incluidas las {@code @Query} nativas de {@link OrdenJpaRepository}. Ninguno
 * de estos 3 adaptadores tenía cobertura salvo la incidental de
 * {@code FronteraTransaccionalIntegrationTest} (Fase 4 del plan de cobertura).
 */
@SpringBootTest(classes = PersistenciaOrdenIntegrationTest.ContextoTest.class)
@ActiveProfiles("test")
class PersistenciaOrdenIntegrationTest {

    @Autowired
    private AdaptadorRepositorioOrden repo;

    @Autowired
    private AdaptadorConsultaOrdenesSoporte consultas;

    @Autowired
    private AdaptadorOrdenesTicketPendiente ticketsPendientes;

    @Autowired
    private OrdenJpaRepository ordenJpaRepository;

    @Autowired
    private ProcesoSagaPrincipalJpaRepository procesoSagaPrincipalJpaRepository;

    @Autowired
    private PlatformTransactionManager transactionManager;

    @PersistenceContext
    private EntityManager entityManager;

    @AfterEach
    void limpiarBaseDeDatos() {
        // Tras la fusión de orden+proceso (fase 2): la satélite (hija, FK a orden) se
        // borra ANTES que orden (ahora el padre).
        procesoSagaPrincipalJpaRepository.deleteAll();
        ordenJpaRepository.deleteAll();
    }

    private static SagaPrincipal nuevaSagaPrincipal(OrdenId id) {
        return SagaPrincipal.crear(id, ExternalId.de(UUID.randomUUID().toString()), DatosNegocioId.nuevo());
    }

    // ------------------------------------------------------------------
    // AdaptadorRepositorioOrden
    // ------------------------------------------------------------------

    @Test
    void crearYCargar_rehidrataElAgregadoCompletoConSuAuditoria() {
        var id = OrdenId.nuevo();
        var saga = nuevaSagaPrincipal(id);
        saga = saga.cancelar(new UsuarioSoporte("ana"), "motivo de negocio"); // deja una entrada de auditoría
        var orden = OrdenRoot.nueva(saga, Instant.now());
        repo.crear(orden);

        var recargada = repo.cargar(id);

        assertThat(recargada.id()).isEqualTo(id);
        assertThat(((SagaPrincipal) recargada.proceso()).estado().name()).isEqualTo("CANCELADA");
        assertThat(recargada.proceso().auditoria()).hasSize(1);
        assertThat(recargada.proceso().auditoria().get(0).accion()).isEqualTo("CANCELAR");
        assertThat(recargada.proceso().auditoria().get(0).quien().usuario()).isEqualTo("ana");
        assertThat(recargada.proceso().auditoria().get(0).detalle()).contains("motivo de negocio");
        assertThat(recargada.version()).isEqualTo(orden.version());
    }

    @Test
    void crearYCargar_conSagaSecundaria2_rehidrataElContextoDesdeSuPropiaSatelite() {
        // Único test que hace un round-trip de SagaSecundaria2 contra JPA/H2 real (los demás
        // tests de este fichero la registran pero no llegan a persistirla ni cargarla): cubre
        // el camino real de guardarContexto/rearmar de SoporteSagaSecundaria2 contra su propia
        // tabla satélite, con Hibernate hidratando ProcesoSagaSecundaria2Entity de verdad.
        var id = OrdenId.nuevo();
        var externalId = ExternalId.de(UUID.randomUUID().toString());
        var ctx = new ContextoArranque.ArranqueSecundaria2(externalId, new RefPaso5("ref5"));
        repo.crear(OrdenRoot.nueva(SagaSecundaria2.crear(id, ctx), Instant.now()));

        var recargada = (SagaSecundaria2) repo.cargar(id).proceso();

        assertThat(recargada.estado().name()).isEqualTo("INICIAL");
        assertThat(recargada.refPaso5().valor()).isEqualTo("ref5");
        assertThat(recargada.refRespuesta()).isNull();
    }

    @Test
    void cargar_procesoInexistente_lanzaIllegalArgumentException() {
        assertThatThrownBy(() -> repo.cargar(OrdenId.nuevo())).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void cargar_conTokenTrabajadorYResultadoAsignados_losRehidrataCorrectamente() {
        var id = OrdenId.nuevo();
        var token = UUID.randomUUID();
        var ahora = Instant.now();
        var error = new DetalleError("java.lang.RuntimeException", "boom");
        repo.crear(OrdenRoot.rehidratar(nuevaSagaPrincipal(id), 0, ahora,
                token, ahora.plusSeconds(600), null, ahora, error, 0L));

        var recargada = repo.cargar(id);

        assertThat(recargada.tokenTrabajador()).isEqualTo(token);
        assertThat(recargada.completadaEn()).isEqualTo(ahora);
        assertThat(recargada.ultimoError()).isEqualTo(error);
    }

    @Test
    void guardar_conConflictoDeVersion_lanzaConcurrenciaOptimistaException() {
        var id = OrdenId.nuevo();
        repo.crear(OrdenRoot.nueva(nuevaSagaPrincipal(id), Instant.now()));
        var copiaA = repo.cargar(id);
        var copiaB = repo.cargar(id);

        copiaA.resetearIntentos();
        repo.guardar(copiaA);

        copiaB.resetearIntentos();
        assertThatThrownBy(() -> repo.guardar(copiaB)).isInstanceOf(ConcurrenciaOptimistaException.class);
    }

    @Test
    void guardar_dentroDeTransaccionExterna_devuelveLaVersionRealYPermiteEncadenarGuardadosSinRecargar() {
        var id = OrdenId.nuevo();
        repo.crear(OrdenRoot.nueva(nuevaSagaPrincipal(id), Instant.now()));
        var tx = new TransactionTemplate(transactionManager);

        // Misma forma que aplicarPasoNormal: varios guardar de la MISMA orden
        // encadenados sobre la instancia que devuelve el anterior, sin recargar.
        tx.executeWithoutResult(estado -> {
            var orden = repo.cargar(id);
            orden.asignarToken(UUID.randomUUID(), Duration.ofMinutes(10), Instant.now());

            var primera = repo.guardar(orden);
            assertThat(primera.version())
                    .as("el flush de guardar() deja en la instancia devuelta la version real, no la de entrada")
                    .isEqualTo(orden.version() + 1);

            primera.renovarLease(Duration.ofMinutes(10), Instant.now().plusSeconds(60));
            var segunda = repo.guardar(primera);
            assertThat(segunda.version()).isEqualTo(orden.version() + 2);
        });

        // Tras el commit la BD refleja DOS incrementos de version: sin el flush de
        // guardar(), ambos UPDATE colapsarían en uno solo al commit (version +1) y las
        // instancias devueltas dentro de la transacción llevarían la version vieja.
        assertThat(repo.cargar(id).version()).isEqualTo(2L);
    }

    @Test
    void guardar_conVersionObsoletaDentroDeTransaccionExterna_lanzaLaExcepcionDeDominioEnLaLlamadaNoEnElCommit() {
        var id = OrdenId.nuevo();
        repo.crear(OrdenRoot.nueva(nuevaSagaPrincipal(id), Instant.now()));
        var copiaObsoleta = repo.cargar(id); // instantánea con la version vieja
        var ganadora = repo.cargar(id);
        ganadora.asignarToken(UUID.randomUUID(), Duration.ofMinutes(10), Instant.now());
        repo.guardar(ganadora); // otro actor escribe primero: la BD avanza de version

        var tx = new TransactionTemplate(transactionManager);
        tx.executeWithoutResult(estado -> {
            copiaObsoleta.resetearIntentos();
            // El conflicto sale de guardar() como la excepción de dominio DENTRO de la
            // transacción (donde ServicioContinuarOrden puede distinguirla del fallo de un
            // paso), no como excepción de Spring en el commit de la frontera @Transactional.
            assertThatThrownBy(() -> repo.guardar(copiaObsoleta))
                    .isInstanceOf(ConcurrenciaOptimistaException.class);
            estado.setRollbackOnly(); // tras el conflicto la transacción ya solo puede deshacerse
        });

        var trasElConflicto = repo.cargar(id);
        assertThat(trasElConflicto.version()).as("el estado de la ganadora queda intacto").isEqualTo(1L);
        assertThat(trasElConflicto.tokenTrabajador()).isEqualTo(ganadora.tokenTrabajador());
    }

    @Test
    void guardar_carreraRealEntreDosHilos_exactamenteUnoGanaYElOtroRecibeConcurrenciaOptimista() throws Exception {
        var id = OrdenId.nuevo();
        repo.crear(OrdenRoot.nueva(nuevaSagaPrincipal(id), Instant.now()));
        var copiaA = repo.cargar(id); // dos pods con la misma instantánea (misma version),
        var copiaB = repo.cargar(id); // como dos workers que vieron la misma candidata
        copiaA.asignarToken(UUID.randomUUID(), Duration.ofMinutes(10), Instant.now());
        copiaB.asignarToken(UUID.randomUUID(), Duration.ofMinutes(10), Instant.now());

        var listos = new CountDownLatch(2);
        var disparo = new CountDownLatch(1);
        var conflictos = new AtomicInteger();
        var executor = Executors.newFixedThreadPool(2);
        try {
            var futuros = List.of(copiaA, copiaB).stream()
                    .map(copia -> executor.submit(() -> {
                        listos.countDown();
                        if (!disparo.await(5, TimeUnit.SECONDS)) {
                            throw new IllegalStateException("la carrera no llegó a dispararse");
                        }
                        try {
                            repo.guardar(copia);
                            return true;
                        } catch (ConcurrenciaOptimistaException e) {
                            conflictos.incrementAndGet();
                            return false;
                        }
                    }))
                    .toList();
            assertThat(listos.await(5, TimeUnit.SECONDS)).isTrue();
            disparo.countDown(); // ambos hilos guardan a la vez sobre la MISMA fila de H2

            var ganadores = 0;
            for (var futuro : futuros) {
                if (futuro.get(10, TimeUnit.SECONDS)) {
                    ganadores++;
                }
            }
            assertThat(ganadores).isEqualTo(1);
            assertThat(conflictos.get()).isEqualTo(1);
        } finally {
            executor.shutdownNow();
        }

        var trasLaCarrera = repo.cargar(id);
        assertThat(trasLaCarrera.version()).as("una sola escritura ganadora").isEqualTo(1L);
        assertThat(trasLaCarrera.tokenTrabajador()).isIn(copiaA.tokenTrabajador(), copiaB.tokenTrabajador());
    }

    @Test
    void mapeadorDe_tipoNoRegistrado_lanzaIllegalStateException() {
        var repoIncompleto = new AdaptadorRepositorioOrden(ordenJpaRepository,
                List.of(new SoporteSagaPrincipal(procesoSagaPrincipalJpaRepository))); // sin el mapeador de SECUNDARIA1
        var id = OrdenId.nuevo();
        var ctx = new ContextoArranque.ArranqueSecundaria1(
                ExternalId.de(UUID.randomUUID().toString()), new RefPaso1("ref1"));
        var orden = OrdenRoot.nueva(SagaSecundaria1.crear(id, ctx), Instant.now());

        assertThatThrownBy(() -> repoIncompleto.crear(orden)).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void buscarEjecutables_soloDevuelveLasCandidatasElegibles() {
        var ahora = Instant.now();
        var idCandidata = OrdenId.nuevo();
        repo.crear(OrdenRoot.rehidratar(nuevaSagaPrincipal(idCandidata), 0, ahora.minusSeconds(5),
                null, null, null, null, null, 0L));
        var idFutura = OrdenId.nuevo();
        repo.crear(OrdenRoot.rehidratar(nuevaSagaPrincipal(idFutura), 0, ahora.plusSeconds(3600),
                null, null, null, null, null, 0L));
        var idConTokenVigente = OrdenId.nuevo();
        repo.crear(OrdenRoot.rehidratar(nuevaSagaPrincipal(idConTokenVigente), 0, ahora.minusSeconds(5),
                UUID.randomUUID(), ahora.plusSeconds(3600), null, null, null, 0L));
        var idFinalizada = OrdenId.nuevo();
        repo.crear(OrdenRoot.rehidratar(nuevaSagaPrincipal(idFinalizada), 0, ahora.minusSeconds(5),
                null, null, null, ahora, null, 0L));

        var candidatas = repo.buscarEjecutables(ahora, 16);

        assertThat(candidatas).extracting(CandidataOrden::ordenId).containsExactly(idCandidata);
        assertThat(candidatas).extracting(CandidataOrden::tipo).containsExactly(SagaPrincipal.TIPO);
    }

    @Test
    void hayEjecutables_esTrueSoloSiHayCandidatas() {
        var ahora = Instant.now();
        assertThat(repo.hayEjecutables(ahora)).isFalse();

        repo.crear(OrdenRoot.rehidratar(nuevaSagaPrincipal(OrdenId.nuevo()), 0, ahora.minusSeconds(5),
                null, null, null, null, null, 0L));

        assertThat(repo.hayEjecutables(ahora)).isTrue();
    }

    @Test
    @Transactional
    void purgarFinalizadasAntesDe_sinCandidatas_devuelveCero() {
        assertThat(repo.purgarFinalizadasAntesDe(Instant.now())).isZero();
    }

    @Test
    @Transactional
    void purgarFinalizadasAntesDe_borraLasFinalizadasAntesDelCorteYRespetaLasDemas() {
        var ahora = Instant.now();
        var idVieja = OrdenId.nuevo();
        repo.crear(OrdenRoot.rehidratar(nuevaSagaPrincipal(idVieja), 0, ahora,
                null, null, null, ahora, null, 0L));
        var idNoFinalizada = OrdenId.nuevo();
        repo.crear(OrdenRoot.rehidratar(nuevaSagaPrincipal(idNoFinalizada), 0, ahora,
                null, null, null, null, null, 0L));
        // Las candidatas de la query nativa de purga solo ven filas YA en BD: forzamos el
        // flush porque crear() (a diferencia de guardar()) no lo hace.
        ordenJpaRepository.flush();
        // actualizada_en se fija en el @PrePersist al momento de crear la fila; un corte
        // muy futuro la incluye sin depender de forzar la marca de tiempo desde fuera.
        var corteFuturo = Instant.now().plusSeconds(3600);

        var borradas = repo.purgarFinalizadasAntesDe(corteFuturo);

        assertThat(borradas).isGreaterThanOrEqualTo(1);
        assertThatThrownBy(() -> repo.cargar(idVieja)).isInstanceOf(IllegalArgumentException.class);
        assertThat(repo.cargar(idNoFinalizada)).isNotNull(); // nunca finalizada: la purga no la toca
    }

    @Test
    @Transactional
    void purgarFinalizadasAntesDe_borraExplicitamenteLaAuditoriaYLaSateliteDeSuTipo() {
        // Ya no hay ON DELETE CASCADE (prohibido, ver CLAUDE.md): si el borrado explícito de
        // las hijas (proceso_auditoria y la satélite) no ocurriera ANTES que el del padre
        // (orden, desde la fusión de la fase 2), el DELETE nativo de orden violaría la FK
        // real que genera Hibernate para proceso_auditoria (@ElementCollection) y este test
        // fallaría con una excepción en vez de completar.
        var ahora = Instant.now();
        var id = OrdenId.nuevo();
        var saga = nuevaSagaPrincipal(id);
        saga = saga.cancelar(new UsuarioSoporte("ana"), "motivo"); // deja una fila real en proceso_auditoria
        repo.crear(OrdenRoot.rehidratar(saga, 0, ahora, null, null, null, ahora, null, 0L));
        ordenJpaRepository.flush();
        assertThat(procesoSagaPrincipalJpaRepository.findById(id.valor())).as("la satélite existe antes de purgar").isPresent();
        var corteFuturo = Instant.now().plusSeconds(3600);

        var borradas = repo.purgarFinalizadasAntesDe(corteFuturo);

        assertThat(borradas).isEqualTo(1);
        assertThat(procesoSagaPrincipalJpaRepository.findById(id.valor()))
                .as("la satélite queda borrada de forma explícita en la purga").isEmpty();
        var auditoriaRestante = ((Number) entityManager
                .createNativeQuery("SELECT COUNT(*) FROM proceso_auditoria WHERE orden_id = :id")
                .setParameter("id", id.valor().toString())
                .getSingleResult()).longValue();
        assertThat(auditoriaRestante).as("la auditoría queda borrada de forma explícita en la purga").isZero();
    }

    // ------------------------------------------------------------------
    // AdaptadorConsultaOrdenesSoporte
    // ------------------------------------------------------------------

    @Test
    void ordenesBloqueadas_devuelveLasDeEscaleraConsumida() {
        var ahora = Instant.now();
        var idBloqueada = OrdenId.nuevo();
        var error = new DetalleError("java.lang.RuntimeException", "boom");
        repo.crear(OrdenRoot.rehidratar(nuevaSagaPrincipal(idBloqueada), 8, ahora,
                null, null, null, null, error, 0L));
        repo.crear(OrdenRoot.rehidratar(nuevaSagaPrincipal(OrdenId.nuevo()), 1, ahora,
                null, null, null, null, null, 0L));

        var bloqueadas = consultas.ordenesBloqueadas();

        assertThat(bloqueadas).extracting(r -> r.id()).contains(idBloqueada);
        assertThat(bloqueadas).allSatisfy(r -> assertThat(r.intentos()).isGreaterThanOrEqualTo(8));
        assertThat(bloqueadas).filteredOn(r -> r.id().equals(idBloqueada))
                .extracting(r -> r.ultimoError()).containsExactly(error);
    }

    @Test
    void ordenesEnEjecucion_devuelveLasDeTokenVigente() {
        var ahora = Instant.now();
        var idEnEjecucion = OrdenId.nuevo();
        repo.crear(OrdenRoot.rehidratar(nuevaSagaPrincipal(idEnEjecucion), 0, ahora,
                UUID.randomUUID(), ahora.plusSeconds(3600), null, null, null, 0L));
        repo.crear(OrdenRoot.rehidratar(nuevaSagaPrincipal(OrdenId.nuevo()), 0, ahora,
                null, null, null, null, null, 0L));

        var enEjecucion = consultas.ordenesEnEjecucion();

        assertThat(enEjecucion).extracting(r -> r.id()).containsExactly(idEnEjecucion);
    }

    @Test
    void ordenesConTicketPendiente_devuelveLasBloqueadasSinTicketAbierto() {
        var ahora = Instant.now();
        var idPendiente = OrdenId.nuevo();
        repo.crear(OrdenRoot.rehidratar(nuevaSagaPrincipal(idPendiente), 8, ahora,
                null, null, null, null, null, 0L));
        var idConTicketYaAbierto = OrdenId.nuevo();
        repo.crear(OrdenRoot.rehidratar(nuevaSagaPrincipal(idConTicketYaAbierto), 8, ahora,
                null, null, ahora, null, null, 0L));

        var pendientes = consultas.ordenesConTicketPendiente();

        assertThat(pendientes).extracting(r -> r.id()).containsExactly(idPendiente);
    }

    @Test
    void buscar_filtraPorEstadoYRangosDeFecha() {
        var ahora = Instant.now();
        var id = OrdenId.nuevo();
        repo.crear(OrdenRoot.rehidratar(nuevaSagaPrincipal(id), 0, ahora, null, null, null, null, null, 0L));

        var porEstado = consultas.buscar(FiltroOrdenes.porEstado("INICIAL"));
        var porRangoIniciada = consultas.buscar(
                FiltroOrdenes.iniciadaEntre(ahora.minusSeconds(60), ahora.plusSeconds(60)));
        var porRangoActualizada = consultas.buscar(
                FiltroOrdenes.actualizadaEntre(ahora.minusSeconds(60), ahora.plusSeconds(60)));
        var sinFiltros = consultas.buscar(new FiltroOrdenes(null, null, null, null, null));

        assertThat(porEstado).extracting(r -> r.id()).contains(id);
        assertThat(porRangoIniciada).extracting(r -> r.id()).contains(id);
        assertThat(porRangoActualizada).extracting(r -> r.id()).contains(id);
        assertThat(sinFiltros).extracting(r -> r.id()).contains(id);
    }

    @Test
    void porExternalId_devuelveTodasLasOrdenesDeLaTramitacion() {
        var externalId = ExternalId.de(UUID.randomUUID().toString());
        var idPrincipal = OrdenId.nuevo();
        var principal = SagaPrincipal.crear(idPrincipal, externalId, DatosNegocioId.nuevo());
        repo.crear(OrdenRoot.nueva(principal, Instant.now()));
        var idSecundaria = OrdenId.nuevo();
        var ctxSecundaria = new ContextoArranque.ArranqueSecundaria1(externalId, new RefPaso1("ref1"));
        repo.crear(OrdenRoot.nueva(SagaSecundaria1.crear(idSecundaria, ctxSecundaria), Instant.now()));

        var detalles = consultas.porExternalId(externalId);

        assertThat(detalles).extracting(d -> d.resumen().id()).containsExactlyInAnyOrder(idPrincipal, idSecundaria);
    }

    @Test
    void detalle_enEstadoActivo_incluyeElPasoPendiente() {
        var id = OrdenId.nuevo();
        repo.crear(OrdenRoot.nueva(nuevaSagaPrincipal(id), Instant.now()));

        var detalle = consultas.detalle(SagaPrincipal.TIPO, id);

        assertThat(detalle.cancelable()).isTrue();
        assertThat(detalle.pasos()).hasSize(1);
        assertThat(detalle.pasos().get(0).nombrePaso()).isEqualTo("PASO1");
        assertThat(detalle.pasos().get(0).datosManualesObligatorios()).isTrue();
        assertThat(detalle.auditoria()).isEmpty();
    }

    @Test
    void detalle_enEstadoTerminal_noTienePasoPendiente() {
        var id = OrdenId.nuevo();
        var saga = nuevaSagaPrincipal(id);
        saga = saga.cancelar(new UsuarioSoporte("ana"), "motivo");
        repo.crear(OrdenRoot.nueva(saga, Instant.now()));

        var detalle = consultas.detalle(SagaPrincipal.TIPO, id);

        assertThat(detalle.cancelable()).isFalse();
        assertThat(detalle.pasos()).isEmpty();
        assertThat(detalle.auditoria()).hasSize(1);
    }

    @Test
    void detalle_ordenInexistente_lanzaIllegalArgumentException() {
        assertThatThrownBy(() -> consultas.detalle(SagaPrincipal.TIPO, OrdenId.nuevo()))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void descriptorDe_tipoNoRegistrado_lanzaIllegalStateException() {
        var consultasIncompletas = new AdaptadorConsultaOrdenesSoporte(ordenJpaRepository,
                List.of(new SoporteSagaPrincipal(procesoSagaPrincipalJpaRepository))); // sin el descriptor de SECUNDARIA1
        var id = OrdenId.nuevo();
        var ctx = new ContextoArranque.ArranqueSecundaria1(
                ExternalId.de(UUID.randomUUID().toString()), new RefPaso1("ref1"));
        repo.crear(OrdenRoot.nueva(SagaSecundaria1.crear(id, ctx), Instant.now()));

        assertThatThrownBy(() -> consultasIncompletas.detalle(SagaSecundaria1.TIPO, id))
                .isInstanceOf(IllegalStateException.class);
    }

    // ------------------------------------------------------------------
    // AdaptadorOrdenesTicketPendiente
    // ------------------------------------------------------------------

    @Test
    void buscar_devuelveLasOrdenesConEscaleraConsumidaYSinTicketAbierto() {
        var ahora = Instant.now();
        var idPendiente = OrdenId.nuevo();
        var error = new DetalleError("java.lang.RuntimeException", "boom");
        repo.crear(OrdenRoot.rehidratar(nuevaSagaPrincipal(idPendiente), 8, ahora,
                null, null, null, null, error, 0L));
        var idPendienteSinError = OrdenId.nuevo();
        repo.crear(OrdenRoot.rehidratar(nuevaSagaPrincipal(idPendienteSinError), 8, ahora,
                null, null, null, null, null, 0L));

        var pendientes = ticketsPendientes.buscar();

        assertThat(pendientes).extracting(p -> p.ordenId()).contains(idPendiente, idPendienteSinError);
        assertThat(pendientes).extracting(p -> p.tipo()).contains(SagaPrincipal.TIPO);
        assertThat(pendientes).filteredOn(p -> p.ordenId().equals(idPendiente))
                .extracting(p -> p.ultimoError()).containsExactly(error);
        assertThat(pendientes).filteredOn(p -> p.ordenId().equals(idPendienteSinError))
                .extracting(p -> p.ultimoError()).containsExactly((DetalleError) null);
    }

    @Test
    void buscar_sinCandidatas_devuelveListaVacia() {
        assertThat(ticketsPendientes.buscar()).isEmpty();
    }

    // ------------------------------------------------------------------
    // OrdenEntity: bookkeeping de infraestructura (creadaEn/actualizadaEn)
    // ------------------------------------------------------------------

    @Test
    void ordenEntity_alPersistirFijaCreadaEnYActualizadaEn() {
        var id = OrdenId.nuevo();
        repo.crear(OrdenRoot.nueva(nuevaSagaPrincipal(id), Instant.now()));

        var entity = ordenJpaRepository.findById(id.valor()).orElseThrow();

        assertThat(entity.getOrdenId()).isEqualTo(id.valor());
        assertThat(entity.getCreadaEn()).isNotNull();
        assertThat(entity.getActualizadaEn()).isNotNull();
    }

    /**
     * Contexto Spring mínimo: solo persistencia real (JPA sobre H2) + los 3
     * adaptadores de este test, con las 4 SPI de saga registradas (igual que
     * ConfiguracionSagas/ConfiguracionOrderManager en producción).
     */
    @Configuration
    @EnableAutoConfiguration
    @EntityScan(basePackageClasses = {OrdenEntity.class, ProcesoSagaPrincipalEntity.class,
            ProcesoSagaSecundaria1Entity.class, ProcesoSagaSecundaria2Entity.class, ProcesoSagaSecundaria3Entity.class})
    @EnableJpaRepositories(basePackageClasses = {OrdenEntity.class, ProcesoSagaPrincipalEntity.class,
            ProcesoSagaSecundaria1Entity.class, ProcesoSagaSecundaria2Entity.class, ProcesoSagaSecundaria3Entity.class})
    static class ContextoTest {

        @Bean
        AdaptadorRepositorioOrden adaptadorRepositorioOrden(OrdenJpaRepository ordenes,
                ProcesoSagaPrincipalJpaRepository repoPrincipal, ProcesoSagaSecundaria1JpaRepository repoSecundaria1,
                ProcesoSagaSecundaria2JpaRepository repoSecundaria2, ProcesoSagaSecundaria3JpaRepository repoSecundaria3) {
            return new AdaptadorRepositorioOrden(ordenes, List.of(
                    new SoporteSagaPrincipal(repoPrincipal), new SoporteSagaSecundaria1(repoSecundaria1),
                    new SoporteSagaSecundaria2(repoSecundaria2), new SoporteSagaSecundaria3(repoSecundaria3)));
        }

        @Bean
        AdaptadorConsultaOrdenesSoporte adaptadorConsultaOrdenesSoporte(OrdenJpaRepository ordenes,
                ProcesoSagaPrincipalJpaRepository repoPrincipal, ProcesoSagaSecundaria1JpaRepository repoSecundaria1,
                ProcesoSagaSecundaria2JpaRepository repoSecundaria2, ProcesoSagaSecundaria3JpaRepository repoSecundaria3) {
            return new AdaptadorConsultaOrdenesSoporte(ordenes, List.of(
                    new SoporteSagaPrincipal(repoPrincipal), new SoporteSagaSecundaria1(repoSecundaria1),
                    new SoporteSagaSecundaria2(repoSecundaria2), new SoporteSagaSecundaria3(repoSecundaria3)));
        }

        @Bean
        AdaptadorOrdenesTicketPendiente adaptadorOrdenesTicketPendiente(OrdenJpaRepository ordenes) {
            return new AdaptadorOrdenesTicketPendiente(ordenes);
        }

        @Bean
        RepositorioOrden repositorioOrdenParaElPuertoGenerico(AdaptadorRepositorioOrden real) {
            return real;
        }
    }
}
