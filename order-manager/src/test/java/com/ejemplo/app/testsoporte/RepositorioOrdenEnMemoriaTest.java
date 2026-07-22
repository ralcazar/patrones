package com.ejemplo.app.testsoporte;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import com.ejemplo.app.business.ordermanager.dominio.ExternalId;
import com.ejemplo.app.business.ordermanager.dominio.OrdenId;
import com.ejemplo.app.business.ordermanager.dominio.OrdenRoot;
import com.ejemplo.app.business.ordermanager.aplicacion.puerto.salida.RepositorioOrden.CandidataOrden;
import com.ejemplo.app.business.ordermanager.dominio.Prioridad;
import com.ejemplo.app.business.ordermanager.dominio.ProcesoFalso;

/**
 * Verifica que {@link RepositorioOrdenEnMemoria#externalIdsFinalizadosAntesDe}
 * y {@link RepositorioOrdenEnMemoria#purgarPorExternalIds} reproducen la
 * MISMA semántica de agrupación por external_id que la query nativa real
 * (ver {@code OrdenJpaRepository.externalIdsFinalizadosAntesDe}), ya que solo
 * las pruebas de integración (H2) ejercitan la query de verdad. Reutiliza
 * {@link ProcesoFalso}, el doble genérico de Proceso para el motor.
 */
class RepositorioOrdenEnMemoriaTest {

    private final RepositorioOrdenEnMemoria repo = new RepositorioOrdenEnMemoria();

    private static OrdenRoot ordenTerminada(ExternalId externalId, Instant completadaEn) {
        var proceso = ProcesoFalso.crear(OrdenId.nuevo(), externalId);
        return OrdenRoot.rehidratar(proceso, 0, completadaEn, null, null, null, completadaEn, null, 0L);
    }

    private static OrdenRoot ordenViva(ExternalId externalId) {
        var proceso = ProcesoFalso.crear(OrdenId.nuevo(), externalId);
        return OrdenRoot.nueva(proceso, Instant.now());
    }

    @Test
    void externalIdsFinalizadosAntesDe_grupoConUnaOrdenVivaQuedaExcluido() {
        var externalId = ExternalId.de(UUID.randomUUID().toString());
        var ahora = Instant.now();
        repo.crear(ordenTerminada(externalId, ahora.minusSeconds(3600)));
        repo.crear(ordenViva(externalId));

        var resultado = repo.externalIdsFinalizadosAntesDe(ahora.plusSeconds(3600));

        assertThat(resultado).doesNotContain(externalId);
    }

    @Test
    void externalIdsFinalizadosAntesDe_grupoTodoTerminadoYAnteriorAlCorteQuedaIncluido() {
        var externalId = ExternalId.de(UUID.randomUUID().toString());
        var ahora = Instant.now();
        repo.crear(ordenTerminada(externalId, ahora.minusSeconds(7200)));
        repo.crear(ordenTerminada(externalId, ahora.minusSeconds(3600)));

        var resultado = repo.externalIdsFinalizadosAntesDe(ahora);

        // La antigüedad del grupo es la MÁS RECIENTE en terminar (MAX completadaEn): aquí
        // ahora - 3600s, anterior al corte "ahora".
        assertThat(resultado).contains(externalId);
    }

    @Test
    void externalIdsFinalizadosAntesDe_grupoTerminadoPeroPosteriorAlCorteQuedaExcluido() {
        var externalId = ExternalId.de(UUID.randomUUID().toString());
        var ahora = Instant.now();
        repo.crear(ordenTerminada(externalId, ahora));

        var resultado = repo.externalIdsFinalizadosAntesDe(ahora.minusSeconds(3600));

        assertThat(resultado).doesNotContain(externalId);
    }

    @Test
    void purgarPorExternalIds_borraSoloLasOrdenesDeLosExternalIdsIndicadosYDevuelveElRecuento() {
        var externalIdABorrar = ExternalId.de(UUID.randomUUID().toString());
        var externalIdAConservar = ExternalId.de(UUID.randomUUID().toString());
        var ahora = Instant.now();
        var idBorrada1 = OrdenId.nuevo();
        var idBorrada2 = OrdenId.nuevo();
        var idConservada = OrdenId.nuevo();
        repo.crear(OrdenRoot.rehidratar(ProcesoFalso.crear(idBorrada1, externalIdABorrar), 0,
                ahora, null, null, null, ahora, null, 0L));
        repo.crear(OrdenRoot.rehidratar(ProcesoFalso.crear(idBorrada2, externalIdABorrar), 0,
                ahora, null, null, null, ahora, null, 0L));
        repo.crear(OrdenRoot.rehidratar(ProcesoFalso.crear(idConservada, externalIdAConservar), 0,
                ahora, null, null, null, ahora, null, 0L));

        var borradas = repo.purgarPorExternalIds(List.of(externalIdABorrar));

        assertThat(borradas).isEqualTo(2);
        assertThat(repo.todas()).extracting(OrdenRoot::id).containsExactly(idConservada);
    }

    @Test
    void buscarEjecutables_ordenaPorPrioridadDescYLuegoPorProximoReintentoEnAsc() {
        var ahora = Instant.now();
        var idBajaPrioridadTemprana = OrdenId.nuevo();
        repo.crear(OrdenRoot.rehidratar(ProcesoFalso.crear(idBajaPrioridadTemprana, ExternalId.de(UUID.randomUUID().toString())),
                Prioridad.normal(), 0, ahora.minusSeconds(10), null, null, null, null, null, 0L));
        var idAltaPrioridadTardia = OrdenId.nuevo();
        repo.crear(OrdenRoot.rehidratar(ProcesoFalso.crear(idAltaPrioridadTardia, ExternalId.de(UUID.randomUUID().toString())),
                new Prioridad(30), 0, ahora.minusSeconds(1), null, null, null, null, null, 0L));
        var idMismaAltaPrioridadMasTemprana = OrdenId.nuevo();
        repo.crear(OrdenRoot.rehidratar(
                ProcesoFalso.crear(idMismaAltaPrioridadMasTemprana, ExternalId.de(UUID.randomUUID().toString())),
                new Prioridad(30), 0, ahora.minusSeconds(5), null, null, null, null, null, 0L));

        var candidatas = repo.buscarEjecutables(ahora, 10);

        assertThat(candidatas).extracting(CandidataOrden::ordenId)
                .containsExactly(idMismaAltaPrioridadMasTemprana, idAltaPrioridadTardia, idBajaPrioridadTemprana);
    }

    @Test
    void purgarPorExternalIds_conListaVaciaNoBorraNada() {
        var externalId = ExternalId.de(UUID.randomUUID().toString());
        repo.crear(ordenTerminada(externalId, Instant.now()));

        var borradas = repo.purgarPorExternalIds(List.of());

        assertThat(borradas).isZero();
        assertThat(repo.todas()).hasSize(1);
    }
}
