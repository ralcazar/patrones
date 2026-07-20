package com.ejemplo.app.infraestructure.sagas.sagasecundaria3.persistencia;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import com.ejemplo.app.business.ordermanager.dominio.ExternalId;
import com.ejemplo.app.business.ordermanager.dominio.OrdenId;
import com.ejemplo.app.business.sagas.dominio.comun.ContextoArranque;
import com.ejemplo.app.business.sagas.dominio.comun.RefPaso7;
import com.ejemplo.app.business.sagas.dominio.sagasecundaria3.EstadoSagaSecundaria3;
import com.ejemplo.app.business.sagas.dominio.sagasecundaria3.RefEjecucion;
import com.ejemplo.app.business.sagas.dominio.sagasecundaria3.ResultadoPasoSecundaria3;
import com.ejemplo.app.business.sagas.dominio.sagasecundaria3.SagaSecundaria3;

/**
 * {@link com.ejemplo.app.infraestructure.ordermanager.persistencia.MapeadorProceso} de la
 * saga secundaria 3. El repo JPA de la satélite se dobla con Mockito (test de
 * infraestructure, ver la nota en {@link SoporteSagaPrincipalTest}).
 */
class SoporteSagaSecundaria3Test {

    private final ProcesoSagaSecundaria3JpaRepository repo = mock(ProcesoSagaSecundaria3JpaRepository.class);
    private final SoporteSagaSecundaria3 soporte = new SoporteSagaSecundaria3(repo);

    @Test
    void tipo_esSecundaria3() {
        assertThat(soporte.tipo()).isEqualTo(SagaSecundaria3.TIPO);
    }

    @Test
    void pasoPendiente_soloEnInicialEsEjecucion() {
        assertThat(soporte.pasoPendiente("INICIAL")).isEqualTo("EJECUCION");
        assertThat(soporte.pasoPendiente("TERMINADA")).isNull();
    }

    @Test
    void datosManualesObligatorios_nuncaLoEs() {
        assertThat(soporte.datosManualesObligatorios("INICIAL")).isFalse();
        assertThat(soporte.datosManualesObligatorios("TERMINADA")).isFalse();
    }

    @Test
    void cancelable_nuncaLoEs() {
        assertThat(soporte.cancelable("INICIAL")).isFalse();
        assertThat(soporte.cancelable("TERMINADA")).isFalse();
    }

    @Test
    void estado_devuelveElNombreDelEstadoDeLaFsm() {
        var saga = SagaSecundaria3.crear(OrdenId.nuevo(),
                new ContextoArranque.ArranqueSecundaria3(ExternalId.de(UUID.randomUUID().toString()), new RefPaso7("ref7")));

        assertThat(soporte.estado(saga)).isEqualTo("INICIAL");
    }

    @Test
    void guardarContexto_sinRefEjecucionGuardaLaEntidadConRefEjecucionANull() {
        var id = OrdenId.nuevo();
        var saga = SagaSecundaria3.crear(id,
                new ContextoArranque.ArranqueSecundaria3(ExternalId.de(UUID.randomUUID().toString()), new RefPaso7("ref7")));
        var captor = ArgumentCaptor.forClass(ProcesoSagaSecundaria3Entity.class);

        soporte.guardarContexto(saga);

        verify(repo).save(captor.capture());
        var entidad = captor.getValue();
        assertThat(entidad.getOrdenId()).isEqualTo(id.valor());
        assertThat(entidad.getRefPaso7()).isEqualTo("ref7");
        assertThat(entidad.getRefEjecucion()).isNull();
    }

    @Test
    void guardarContexto_conRefEjecucionGuardaLaEntidadCompleta() {
        var id = OrdenId.nuevo();
        var saga = SagaSecundaria3.crear(id,
                new ContextoArranque.ArranqueSecundaria3(ExternalId.de(UUID.randomUUID().toString()), new RefPaso7("ref7")));
        saga = saga.aplicarYAvanzar(new ResultadoPasoSecundaria3.Ejecutada(new RefEjecucion("refEjecucion")));
        var captor = ArgumentCaptor.forClass(ProcesoSagaSecundaria3Entity.class);

        soporte.guardarContexto(saga);

        verify(repo).save(captor.capture());
        assertThat(captor.getValue().getRefEjecucion()).isEqualTo("refEjecucion");
        assertThat(soporte.estado(saga)).isEqualTo("TERMINADA");
    }

    @Test
    void rearmar_conEntidadSinRefEjecucionReconstruyeElContextoConNull() {
        var id = OrdenId.nuevo();
        var externalId = ExternalId.de(UUID.randomUUID().toString());
        var entidad = new ProcesoSagaSecundaria3Entity(id.valor(), "ref7", null);
        when(repo.findById(id.valor())).thenReturn(Optional.of(entidad));

        var rearmada = (SagaSecundaria3) soporte.rearmar(id, externalId, "INICIAL", List.of());

        assertThat(rearmada.estado()).isEqualTo(EstadoSagaSecundaria3.INICIAL);
        assertThat(rearmada.refPaso7().valor()).isEqualTo("ref7");
        assertThat(rearmada.refEjecucion()).isNull();
    }

    @Test
    void rearmar_conEntidadCompletaReconstruyeElContextoConLaRefEjecucion() {
        var id = OrdenId.nuevo();
        var externalId = ExternalId.de(UUID.randomUUID().toString());
        var entidad = new ProcesoSagaSecundaria3Entity(id.valor(), "ref7", "refEjecucion");
        when(repo.findById(id.valor())).thenReturn(Optional.of(entidad));

        var rearmada = (SagaSecundaria3) soporte.rearmar(id, externalId, "TERMINADA", List.of());

        assertThat(rearmada.estado()).isEqualTo(EstadoSagaSecundaria3.TERMINADA);
        assertThat(rearmada.refEjecucion().valor()).isEqualTo("refEjecucion");
    }

    @Test
    void rearmar_sinFilaEnLaSateliteLanzaIllegalArgumentException() {
        var id = OrdenId.nuevo();
        when(repo.findById(id.valor())).thenReturn(Optional.empty());

        assertThatThrownBy(() -> soporte.rearmar(id, ExternalId.de(UUID.randomUUID().toString()), "INICIAL", List.of()))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void borrarContexto_delegaEnElBorradoPorLoteDelRepo() {
        var ids = List.of(UUID.randomUUID());

        soporte.borrarContexto(ids);

        verify(repo).borrarPorIds(ids);
    }
}
