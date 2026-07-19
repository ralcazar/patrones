package com.ejemplo.app.infraestructure.sagas.persistencia;

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
import com.ejemplo.app.business.sagas.dominio.comun.RefPaso1;
import com.ejemplo.app.business.sagas.dominio.sagasecundaria1.EstadoSagaSecundaria1;
import com.ejemplo.app.business.sagas.dominio.sagasecundaria1.ResultadoPasoSecundaria1;
import com.ejemplo.app.business.sagas.dominio.sagasecundaria1.SagaSecundaria1;

/**
 * {@link com.ejemplo.app.infraestructure.ordermanager.persistencia.MapeadorProceso} de la
 * saga secundaria 1. El repo JPA de la satélite se dobla con Mockito (test de
 * infraestructure, ver la nota en {@link SoporteSagaPrincipalTest}).
 */
class SoporteSagaSecundaria1Test {

    private final ProcesoSagaSecundaria1JpaRepository repo = mock(ProcesoSagaSecundaria1JpaRepository.class);
    private final SoporteSagaSecundaria1 soporte = new SoporteSagaSecundaria1(repo);

    @Test
    void tipo_esSecundaria1() {
        assertThat(soporte.tipo()).isEqualTo(SagaSecundaria1.TIPO);
    }

    @Test
    void pasoPendiente() {
        assertThat(soporte.pasoPendiente("INICIAL")).isEqualTo("INICIO");
        assertThat(soporte.pasoPendiente("INICIO_HECHO")).isEqualTo("CONFIRMACION");
        assertThat(soporte.pasoPendiente("TERMINADA")).isNull();
    }

    @Test
    void datosManualesObligatorios_soloEnInicial() {
        assertThat(soporte.datosManualesObligatorios("INICIAL")).isTrue();
        assertThat(soporte.datosManualesObligatorios("INICIO_HECHO")).isFalse();
    }

    @Test
    void cancelable_nuncaLoEs() {
        assertThat(soporte.cancelable("INICIAL")).isFalse();
        assertThat(soporte.cancelable("TERMINADA")).isFalse();
    }

    @Test
    void estado_devuelveElNombreDelEstadoDeLaFsm() {
        var saga = SagaSecundaria1.crear(OrdenId.nuevo(),
                new ContextoArranque.ArranqueSecundaria1(ExternalId.de(UUID.randomUUID().toString()), new RefPaso1("ref1")));

        assertThat(soporte.estado(saga)).isEqualTo("INICIAL");
    }

    @Test
    void guardarContexto_conSoloRefPaso1GuardaLaEntidadConLasRefsOpcionalesANull() {
        var id = OrdenId.nuevo();
        var saga = SagaSecundaria1.crear(id,
                new ContextoArranque.ArranqueSecundaria1(ExternalId.de(UUID.randomUUID().toString()), new RefPaso1("ref1")));
        var captor = ArgumentCaptor.forClass(ProcesoSagaSecundaria1Entity.class);

        soporte.guardarContexto(saga);

        verify(repo).save(captor.capture());
        var entidad = captor.getValue();
        assertThat(entidad.getOrdenId()).isEqualTo(id.valor());
        assertThat(entidad.getRefPaso1()).isEqualTo("ref1");
        assertThat(entidad.getRefInicio()).isNull();
        assertThat(entidad.getRefConfirmacion()).isNull();
    }

    @Test
    void guardarContexto_conTodasLasRefsRellenasGuardaLaEntidadCompleta() {
        var id = OrdenId.nuevo();
        var saga = SagaSecundaria1.crear(id,
                new ContextoArranque.ArranqueSecundaria1(ExternalId.de(UUID.randomUUID().toString()), new RefPaso1("ref1")));
        saga = saga.aplicarYAvanzar(new ResultadoPasoSecundaria1.Iniciada(
                new com.ejemplo.app.business.sagas.dominio.sagasecundaria1.RefInicio("refInicio")));
        saga = saga.aplicarYAvanzar(new ResultadoPasoSecundaria1.Confirmada(
                new com.ejemplo.app.business.sagas.dominio.sagasecundaria1.RefConfirmacion("refConfirmacion")));
        var captor = ArgumentCaptor.forClass(ProcesoSagaSecundaria1Entity.class);

        soporte.guardarContexto(saga);

        verify(repo).save(captor.capture());
        var entidad = captor.getValue();
        assertThat(entidad.getRefInicio()).isEqualTo("refInicio");
        assertThat(entidad.getRefConfirmacion()).isEqualTo("refConfirmacion");
        assertThat(soporte.estado(saga)).isEqualTo("TERMINADA");
    }

    @Test
    void rearmar_conEntidadSinRefsOpcionalesReconstruyeElContextoConNulls() {
        var id = OrdenId.nuevo();
        var externalId = ExternalId.de(UUID.randomUUID().toString());
        var entidad = new ProcesoSagaSecundaria1Entity(id.valor(), "ref1", null, null);
        when(repo.findById(id.valor())).thenReturn(Optional.of(entidad));

        var rearmada = (SagaSecundaria1) soporte.rearmar(id, externalId, "INICIAL", List.of());

        assertThat(rearmada.estado()).isEqualTo(EstadoSagaSecundaria1.INICIAL);
        assertThat(rearmada.refPaso1().valor()).isEqualTo("ref1");
        assertThat(rearmada.refInicio()).isNull();
        assertThat(rearmada.refConfirmacion()).isNull();
    }

    @Test
    void rearmar_conEntidadCompletaReconstruyeElContextoConTodasLasRefs() {
        var id = OrdenId.nuevo();
        var externalId = ExternalId.de(UUID.randomUUID().toString());
        var entidad = new ProcesoSagaSecundaria1Entity(id.valor(), "ref1", "refInicio", "refConfirmacion");
        when(repo.findById(id.valor())).thenReturn(Optional.of(entidad));

        var rearmada = (SagaSecundaria1) soporte.rearmar(id, externalId, "TERMINADA", List.of());

        assertThat(rearmada.estado()).isEqualTo(EstadoSagaSecundaria1.TERMINADA);
        assertThat(rearmada.refInicio().valor()).isEqualTo("refInicio");
        assertThat(rearmada.refConfirmacion().valor()).isEqualTo("refConfirmacion");
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
