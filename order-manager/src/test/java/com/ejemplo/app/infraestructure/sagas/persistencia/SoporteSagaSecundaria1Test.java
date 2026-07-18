package com.ejemplo.app.infraestructure.sagas.persistencia;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import com.ejemplo.app.business.ordermanager.dominio.ExternalId;
import com.ejemplo.app.business.ordermanager.dominio.OrdenId;
import com.ejemplo.app.business.sagas.dominio.comun.ContextoArranque;
import com.ejemplo.app.business.sagas.dominio.comun.RefPaso1;
import com.ejemplo.app.business.sagas.dominio.sagasecundaria1.EstadoSagaSecundaria1;
import com.ejemplo.app.business.sagas.dominio.sagasecundaria1.ResultadoPasoSecundaria1;
import com.ejemplo.app.business.sagas.dominio.sagasecundaria1.SagaSecundaria1;

/** {@link com.ejemplo.app.infraestructure.ordermanager.persistencia.MapeadorProceso} de la saga secundaria 1. */
class SoporteSagaSecundaria1Test {

    private final SoporteSagaSecundaria1 soporte = new SoporteSagaSecundaria1();

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
    void desarmarYRearmar_conSoloRefPaso1HaceIdaYVuelta() {
        var id = OrdenId.nuevo();
        var externalId = ExternalId.de(UUID.randomUUID().toString());
        var saga = SagaSecundaria1.crear(id, new ContextoArranque.ArranqueSecundaria1(externalId, new RefPaso1("ref1")));

        var persistible = soporte.desarmar(saga);
        var rearmada = (SagaSecundaria1) soporte.rearmar(id, externalId, persistible.estado(),
                persistible.contexto(), List.of());

        assertThat(persistible.estado()).isEqualTo("INICIAL");
        assertThat(persistible.contexto()).containsEntry("refPaso1", "ref1")
                .doesNotContainKeys("refInicio", "refConfirmacion");
        assertThat(rearmada.estado()).isEqualTo(EstadoSagaSecundaria1.INICIAL);
        assertThat(rearmada.refPaso1()).isEqualTo(saga.refPaso1());
        assertThat(rearmada.refInicio()).isNull();
        assertThat(rearmada.refConfirmacion()).isNull();
    }

    @Test
    void desarmarYRearmar_conTodasLasRefsRellenasHaceIdaYVuelta() {
        var id = OrdenId.nuevo();
        var externalId = ExternalId.de(UUID.randomUUID().toString());
        var saga = SagaSecundaria1.crear(id, new ContextoArranque.ArranqueSecundaria1(externalId, new RefPaso1("ref1")));
        saga.aplicarYAvanzar(new ResultadoPasoSecundaria1.Iniciada(
                new com.ejemplo.app.business.sagas.dominio.sagasecundaria1.RefInicio("refInicio")));
        saga.aplicarYAvanzar(new ResultadoPasoSecundaria1.Confirmada(
                new com.ejemplo.app.business.sagas.dominio.sagasecundaria1.RefConfirmacion("refConfirmacion")));

        var persistible = soporte.desarmar(saga);
        var rearmada = (SagaSecundaria1) soporte.rearmar(id, externalId, persistible.estado(),
                persistible.contexto(), List.of());

        assertThat(persistible.estado()).isEqualTo("TERMINADA");
        assertThat(persistible.contexto()).containsEntry("refInicio", "refInicio")
                .containsEntry("refConfirmacion", "refConfirmacion");
        assertThat(rearmada.estado()).isEqualTo(EstadoSagaSecundaria1.TERMINADA);
        assertThat(rearmada.refInicio()).isEqualTo(saga.refInicio());
        assertThat(rearmada.refConfirmacion()).isEqualTo(saga.refConfirmacion());
    }
}
