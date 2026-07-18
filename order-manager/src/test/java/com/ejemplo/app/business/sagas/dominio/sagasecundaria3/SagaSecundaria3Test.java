package com.ejemplo.app.business.sagas.dominio.sagasecundaria3;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.UUID;

import org.junit.jupiter.api.Test;

import com.ejemplo.app.business.ordermanager.dominio.ExternalId;
import com.ejemplo.app.business.ordermanager.dominio.OrdenId;
import com.ejemplo.app.business.ordermanager.dominio.PasoNoIntervenibleException;
import com.ejemplo.app.business.ordermanager.dominio.ResultadoOrden;
import com.ejemplo.app.business.ordermanager.dominio.UsuarioSoporte;
import com.ejemplo.app.business.sagas.dominio.comun.ContextoArranque;
import com.ejemplo.app.business.sagas.dominio.comun.RefPaso7;
import com.ejemplo.app.business.sagas.dominio.sagasecundaria1.RefInicio;
import com.ejemplo.app.business.sagas.dominio.sagasecundaria1.ResultadoPasoSecundaria1;

/** Saga secundaria 3: un único paso síncrono, INICIAL -&gt; TERMINADA. */
class SagaSecundaria3Test {

    private static SagaSecundaria3 nueva() {
        var ctx = new ContextoArranque.ArranqueSecundaria3(
                ExternalId.de(UUID.randomUUID().toString()), new RefPaso7("ref7"));
        return SagaSecundaria3.crear(OrdenId.nuevo(), ctx);
    }

    @Test
    void crear_arrancaEnInicialConComandoEjecutar() {
        var saga = nueva();

        assertThat(saga.estado()).isEqualTo(EstadoSagaSecundaria3.INICIAL);
        assertThat(saga.comandoActual()).isInstanceOf(ComandoPasoSecundaria3.Ejecutar.class);
        assertThat(saga.terminada()).isFalse();
    }

    @Test
    void aplicarYAvanzar_ejecutada_terminaLaSagaConLaRef() {
        var saga = nueva();

        saga.aplicarYAvanzar(new ResultadoPasoSecundaria3.Ejecutada(new RefEjecucion("ejec1")));

        assertThat(saga.estado()).isEqualTo(EstadoSagaSecundaria3.TERMINADA);
        assertThat(saga.terminada()).isTrue();
        assertThat(saga.refEjecucion().valor()).isEqualTo("ejec1");
        assertThat(saga.resultadoFinal()).isEqualTo(ResultadoOrden.FINALIZADA_OK);
    }

    @Test
    void comandoActual_enTerminadaNoTienePasoPendiente() {
        var saga = nueva();
        saga.aplicarYAvanzar(new ResultadoPasoSecundaria3.Ejecutada(new RefEjecucion("ejec1")));

        assertThatThrownBy(saga::comandoActual).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void aplicarYAvanzar_conResultadoDeOtraSaga_lanzaIllegalArgumentException() {
        var saga = nueva();

        assertThatThrownBy(() -> saga.aplicarYAvanzar(new ResultadoPasoSecundaria1.Iniciada(new RefInicio("x"))))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void marcarPasoActualOkManual_enInicial_terminaLaSagaManualmente() {
        var saga = nueva();

        saga.marcarPasoActualOkManual(new UsuarioSoporte("ana"), "motivo", null);

        assertThat(saga.estado()).isEqualTo(EstadoSagaSecundaria3.TERMINADA);
    }

    @Test
    void marcarPasoActualOkManual_enTerminada_lanzaPasoNoIntervenibleException() {
        var saga = nueva();
        saga.aplicarYAvanzar(new ResultadoPasoSecundaria3.Ejecutada(new RefEjecucion("ejec1")));

        assertThatThrownBy(() -> saga.marcarPasoActualOkManual(new UsuarioSoporte("ana"), "motivo", null))
                .isInstanceOf(PasoNoIntervenibleException.class);
    }
}
