package com.ejemplo.app.infraestructure.ordermanager.persistencia;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

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
import org.springframework.transaction.annotation.Transactional;

import com.ejemplo.app.business.ordermanager.aplicacion.puerto.entrada.CasoUsoConsultarOrdenesSoporte.FiltroOrdenes;
import com.ejemplo.app.business.ordermanager.aplicacion.puerto.salida.RepositorioOrden;
import com.ejemplo.app.business.ordermanager.aplicacion.puerto.salida.RepositorioOrden.CandidataOrden;
import com.ejemplo.app.business.ordermanager.dominio.ConcurrenciaOptimistaException;
import com.ejemplo.app.business.ordermanager.dominio.ExternalId;
import com.ejemplo.app.business.ordermanager.dominio.OrdenId;
import com.ejemplo.app.business.ordermanager.dominio.OrdenRoot;
import com.ejemplo.app.business.ordermanager.dominio.TipoOrden;
import com.ejemplo.app.business.ordermanager.dominio.UsuarioSoporte;
import com.ejemplo.app.business.sagas.dominio.comun.ContextoArranque;
import com.ejemplo.app.business.sagas.dominio.sagaprincipal.DatoNegocio2;
import com.ejemplo.app.business.sagas.dominio.sagaprincipal.DatoNegocio3;
import com.ejemplo.app.business.sagas.dominio.sagaprincipal.SagaPrincipal;
import com.ejemplo.app.business.sagas.dominio.comun.RefPaso1;
import com.ejemplo.app.business.sagas.dominio.sagasecundaria1.SagaSecundaria1;
import com.ejemplo.app.infraestructure.sagas.persistencia.SoporteSagaPrincipal;
import com.ejemplo.app.infraestructure.sagas.persistencia.SoporteSagaSecundaria1;
import com.ejemplo.app.infraestructure.sagas.persistencia.SoporteSagaSecundaria2;
import com.ejemplo.app.infraestructure.sagas.persistencia.SoporteSagaSecundaria3;

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
    private ProcesoJpaRepository procesoJpaRepository;

    @AfterEach
    void limpiarBaseDeDatos() {
        ordenJpaRepository.deleteAll();
        procesoJpaRepository.deleteAll();
    }

    private static SagaPrincipal nuevaSagaPrincipal(OrdenId id) {
        return SagaPrincipal.crear(id, ExternalId.de(UUID.randomUUID().toString()),
                new DatoNegocio3("v1", "v2"), new DatoNegocio2("v1", "v2"));
    }

    // ------------------------------------------------------------------
    // AdaptadorRepositorioOrden
    // ------------------------------------------------------------------

    @Test
    void crearYCargar_rehidrataElAgregadoCompletoConSuAuditoria() {
        var id = OrdenId.nuevo();
        var saga = nuevaSagaPrincipal(id);
        saga.cancelar(new UsuarioSoporte("ana"), "motivo de negocio"); // deja una entrada de auditoría
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
    void cargar_procesoInexistente_lanzaIllegalArgumentException() {
        assertThatThrownBy(() -> repo.cargar(OrdenId.nuevo())).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void cargar_conProcesoPeroSinFilaDeOrden_lanzaIllegalArgumentException() {
        var id = OrdenId.nuevo();
        // Fila de proceso sin su correspondiente fila de orden (inconsistencia entre las dos
        // tablas del agregado): ejercita el segundo orElseThrow de cargar(), distinto del primero.
        procesoJpaRepository.save(new ProcesoEntity(id.valor(), SagaPrincipal.TIPO.valor(),
                UUID.randomUUID().toString(), "INICIAL", "{}", List.of()));

        assertThatThrownBy(() -> repo.cargar(id)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void cargar_conTokenTrabajadorYResultadoAsignados_losRehidrataCorrectamente() {
        var id = OrdenId.nuevo();
        var token = UUID.randomUUID();
        var ahora = Instant.now();
        repo.crear(OrdenRoot.rehidratar(nuevaSagaPrincipal(id), 0, ahora,
                token, ahora.plusSeconds(600), null, ahora, 0L));

        var recargada = repo.cargar(id);

        assertThat(recargada.tokenTrabajador()).isEqualTo(token);
        assertThat(recargada.completadaEn()).isEqualTo(ahora);
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
    void mapeadorDe_tipoNoRegistrado_lanzaIllegalStateException() {
        var repoIncompleto = new AdaptadorRepositorioOrden(ordenJpaRepository, procesoJpaRepository,
                List.of(new SoporteSagaPrincipal())); // sin el mapeador de SECUNDARIA1
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
                null, null, null, null, 0L));
        var idFutura = OrdenId.nuevo();
        repo.crear(OrdenRoot.rehidratar(nuevaSagaPrincipal(idFutura), 0, ahora.plusSeconds(3600),
                null, null, null, null, 0L));
        var idConTokenVigente = OrdenId.nuevo();
        repo.crear(OrdenRoot.rehidratar(nuevaSagaPrincipal(idConTokenVigente), 0, ahora.minusSeconds(5),
                UUID.randomUUID(), ahora.plusSeconds(3600), null, null, 0L));
        var idFinalizada = OrdenId.nuevo();
        repo.crear(OrdenRoot.rehidratar(nuevaSagaPrincipal(idFinalizada), 0, ahora.minusSeconds(5),
                null, null, null, ahora, 0L));

        var candidatas = repo.buscarEjecutables(ahora, 16);

        assertThat(candidatas).extracting(CandidataOrden::ordenId).containsExactly(idCandidata);
        assertThat(candidatas).extracting(CandidataOrden::tipo).containsExactly(SagaPrincipal.TIPO);
    }

    @Test
    void hayEjecutables_esTrueSoloSiHayCandidatas() {
        var ahora = Instant.now();
        assertThat(repo.hayEjecutables(ahora)).isFalse();

        repo.crear(OrdenRoot.rehidratar(nuevaSagaPrincipal(OrdenId.nuevo()), 0, ahora.minusSeconds(5),
                null, null, null, null, 0L));

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
                null, null, null, ahora, 0L));
        var idNoFinalizada = OrdenId.nuevo();
        repo.crear(OrdenRoot.rehidratar(nuevaSagaPrincipal(idNoFinalizada), 0, ahora,
                null, null, null, null, 0L));
        // Las candidatas de la query nativa de purga solo ven filas YA en BD: forzamos el
        // flush porque crear() (a diferencia de guardar()) no lo hace.
        ordenJpaRepository.flush();
        procesoJpaRepository.flush();
        // actualizada_en se fija en el @PrePersist al momento de crear la fila; un corte
        // muy futuro la incluye sin depender de forzar la marca de tiempo desde fuera.
        var corteFuturo = Instant.now().plusSeconds(3600);

        var borradas = repo.purgarFinalizadasAntesDe(corteFuturo);

        assertThat(borradas).isGreaterThanOrEqualTo(1);
        assertThatThrownBy(() -> repo.cargar(idVieja)).isInstanceOf(IllegalArgumentException.class);
        assertThat(repo.cargar(idNoFinalizada)).isNotNull(); // nunca finalizada: la purga no la toca
    }

    // ------------------------------------------------------------------
    // AdaptadorConsultaOrdenesSoporte
    // ------------------------------------------------------------------

    @Test
    void ordenesBloqueadas_devuelveLasDeEscaleraConsumida() {
        var ahora = Instant.now();
        var idBloqueada = OrdenId.nuevo();
        repo.crear(OrdenRoot.rehidratar(nuevaSagaPrincipal(idBloqueada), 8, ahora,
                null, null, null, null, 0L));
        repo.crear(OrdenRoot.rehidratar(nuevaSagaPrincipal(OrdenId.nuevo()), 1, ahora,
                null, null, null, null, 0L));

        var bloqueadas = consultas.ordenesBloqueadas();

        assertThat(bloqueadas).extracting(r -> r.id()).contains(idBloqueada);
        assertThat(bloqueadas).allSatisfy(r -> assertThat(r.intentos()).isGreaterThanOrEqualTo(8));
    }

    @Test
    void ordenesEnEjecucion_devuelveLasDeTokenVigente() {
        var ahora = Instant.now();
        var idEnEjecucion = OrdenId.nuevo();
        repo.crear(OrdenRoot.rehidratar(nuevaSagaPrincipal(idEnEjecucion), 0, ahora,
                UUID.randomUUID(), ahora.plusSeconds(3600), null, null, 0L));
        repo.crear(OrdenRoot.rehidratar(nuevaSagaPrincipal(OrdenId.nuevo()), 0, ahora,
                null, null, null, null, 0L));

        var enEjecucion = consultas.ordenesEnEjecucion();

        assertThat(enEjecucion).extracting(r -> r.id()).containsExactly(idEnEjecucion);
    }

    @Test
    void ordenesConTicketPendiente_devuelveLasBloqueadasSinTicketAbierto() {
        var ahora = Instant.now();
        var idPendiente = OrdenId.nuevo();
        repo.crear(OrdenRoot.rehidratar(nuevaSagaPrincipal(idPendiente), 8, ahora,
                null, null, null, null, 0L));
        var idConTicketYaAbierto = OrdenId.nuevo();
        repo.crear(OrdenRoot.rehidratar(nuevaSagaPrincipal(idConTicketYaAbierto), 8, ahora,
                null, null, ahora, null, 0L));

        var pendientes = consultas.ordenesConTicketPendiente();

        assertThat(pendientes).extracting(r -> r.id()).containsExactly(idPendiente);
    }

    @Test
    void buscar_filtraPorEstadoYRangosDeFecha() {
        var ahora = Instant.now();
        var id = OrdenId.nuevo();
        repo.crear(OrdenRoot.rehidratar(nuevaSagaPrincipal(id), 0, ahora, null, null, null, null, 0L));

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
        var principal = SagaPrincipal.crear(idPrincipal, externalId, new DatoNegocio3("v1", "v2"),
                new DatoNegocio2("v1", "v2"));
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
        saga.cancelar(new UsuarioSoporte("ana"), "motivo");
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
        var consultasIncompletas = new AdaptadorConsultaOrdenesSoporte(ordenJpaRepository, procesoJpaRepository,
                List.of(new SoporteSagaPrincipal())); // sin el descriptor de SECUNDARIA1
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
        repo.crear(OrdenRoot.rehidratar(nuevaSagaPrincipal(idPendiente), 8, ahora,
                null, null, null, null, 0L));

        var pendientes = ticketsPendientes.buscar();

        assertThat(pendientes).extracting(p -> p.ordenId()).contains(idPendiente);
        assertThat(pendientes).extracting(p -> p.tipo()).contains(SagaPrincipal.TIPO);
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
    @EntityScan(basePackageClasses = OrdenEntity.class)
    @EnableJpaRepositories(basePackageClasses = OrdenEntity.class)
    static class ContextoTest {

        @Bean
        AdaptadorRepositorioOrden adaptadorRepositorioOrden(OrdenJpaRepository ordenes, ProcesoJpaRepository procesos) {
            return new AdaptadorRepositorioOrden(ordenes, procesos, List.of(
                    new SoporteSagaPrincipal(), new SoporteSagaSecundaria1(),
                    new SoporteSagaSecundaria2(), new SoporteSagaSecundaria3()));
        }

        @Bean
        AdaptadorConsultaOrdenesSoporte adaptadorConsultaOrdenesSoporte(OrdenJpaRepository ordenes, ProcesoJpaRepository procesos) {
            return new AdaptadorConsultaOrdenesSoporte(ordenes, procesos, List.of(
                    new SoporteSagaPrincipal(), new SoporteSagaSecundaria1(),
                    new SoporteSagaSecundaria2(), new SoporteSagaSecundaria3()));
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
