package com.ejemplo.app.business.sagas.dominio.sagasecundaria3;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.UUID;

import org.junit.jupiter.api.Test;

import com.ejemplo.app.business.ordermanager.dominio.ExternalId;
import com.ejemplo.app.business.ordermanager.dominio.OrdenId;
import com.ejemplo.app.business.ordermanager.dominio.PasoNoIntervenibleException;
import com.ejemplo.app.business.ordermanager.dominio.UsuarioSoporte;
import com.ejemplo.app.business.sagas.dominio.comun.ContextoArranque;
import com.ejemplo.app.business.sagas.dominio.comun.RefPaso7;
import com.ejemplo.app.business.sagas.dominio.sagasecundaria1.RefInicio;
import com.ejemplo.app.business.sagas.dominio.sagasecundaria1.ResultadoPasoSecundaria1;

/**
 * Saga secundaria 3: un único paso síncrono, INICIAL -&gt; TERMINADA.
 *
 * {@code SagaSecundaria3} es un value object inmutable: cada transición
 * devuelve una instancia nueva, así que los tests reasignan la variable
 * local tras cada llamada en vez de mutar "in place".
 */
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

        saga = saga.aplicarYAvanzar(new ResultadoPasoSecundaria3.Ejecutada(new RefEjecucion("ejec1")));

        assertThat(saga.estado()).isEqualTo(EstadoSagaSecundaria3.TERMINADA);
        assertThat(saga.terminada()).isTrue();
        assertThat(saga.refEjecucion().valor()).isEqualTo("ejec1");
    }

    @Test
    void comandoActual_enTerminadaNoTienePasoPendiente() {
        var saga = nueva();
        saga = saga.aplicarYAvanzar(new ResultadoPasoSecundaria3.Ejecutada(new RefEjecucion("ejec1")));

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

        saga = saga.marcarPasoActualOkManual(new UsuarioSoporte("ana"), "motivo", null);

        assertThat(saga.estado()).isEqualTo(EstadoSagaSecundaria3.TERMINADA);
    }

    @Test
    void marcarPasoActualOkManual_enTerminada_lanzaPasoNoIntervenibleException() {
        var saga = nueva();
        saga = saga.aplicarYAvanzar(new ResultadoPasoSecundaria3.Ejecutada(new RefEjecucion("ejec1")));
        var terminada = saga;

        assertThatThrownBy(() -> terminada.marcarPasoActualOkManual(new UsuarioSoporte("ana"), "motivo", null))
                .isInstanceOf(PasoNoIntervenibleException.class);
    }

    // ------------------------------------------------------------------
    // equals/hashCode: value object -- igualdad por (id, externalId, estado,
    // auditoria) heredados de Proceso, más refPaso7/refEjecucion.
    // ------------------------------------------------------------------

    @Test
    void equals_esReflexivo_mismaInstancia() {
        var saga = nueva();

        assertThat(saga.equals(saga)).isTrue();
    }

    @Test
    void equals_conProcesoDeOtroTipo_esFalse() {
        var id = OrdenId.nuevo();
        var externalId = ExternalId.de(UUID.randomUUID().toString());
        var secundaria3 = SagaSecundaria3.rehidratar(id, externalId, new RefPaso7("ref7"), null,
                EstadoSagaSecundaria3.INICIAL, java.util.List.of());
        var secundaria1 = com.ejemplo.app.business.sagas.dominio.sagasecundaria1.SagaSecundaria1.rehidratar(
                id, externalId, new com.ejemplo.app.business.sagas.dominio.comun.RefPaso1("ref1"), null, null,
                com.ejemplo.app.business.sagas.dominio.sagasecundaria1.EstadoSagaSecundaria1.INICIAL, java.util.List.of());

        assertThat(secundaria3.equals(secundaria1)).isFalse();
    }

    @Test
    void equals_conRefPaso7Distinta_esFalse() {
        var id = OrdenId.nuevo();
        var externalId = ExternalId.de(UUID.randomUUID().toString());
        var uno = SagaSecundaria3.rehidratar(id, externalId, new RefPaso7("ref7"), null,
                EstadoSagaSecundaria3.INICIAL, java.util.List.of());
        var otro = SagaSecundaria3.rehidratar(id, externalId, new RefPaso7("otro-ref7"), null,
                EstadoSagaSecundaria3.INICIAL, java.util.List.of());

        assertThat(uno.equals(otro)).isFalse();
    }

    @Test
    void equals_conRefEjecucionDistinta_esFalse() {
        var id = OrdenId.nuevo();
        var externalId = ExternalId.de(UUID.randomUUID().toString());
        var uno = SagaSecundaria3.rehidratar(id, externalId, new RefPaso7("ref7"), new RefEjecucion("ejec1"),
                EstadoSagaSecundaria3.TERMINADA, java.util.List.of());
        var otro = SagaSecundaria3.rehidratar(id, externalId, new RefPaso7("ref7"), new RefEjecucion("ejec2"),
                EstadoSagaSecundaria3.TERMINADA, java.util.List.of());

        assertThat(uno.equals(otro)).isFalse();
    }

    @Test
    void equals_conTodosLosCamposIguales_esTrue() {
        var id = OrdenId.nuevo();
        var externalId = ExternalId.de(UUID.randomUUID().toString());
        var uno = SagaSecundaria3.rehidratar(id, externalId, new RefPaso7("ref7"), new RefEjecucion("ejec1"),
                EstadoSagaSecundaria3.TERMINADA, java.util.List.of());
        var otro = SagaSecundaria3.rehidratar(id, externalId, new RefPaso7("ref7"), new RefEjecucion("ejec1"),
                EstadoSagaSecundaria3.TERMINADA, java.util.List.of());

        assertThat(uno).isEqualTo(otro);
        assertThat(uno.hashCode()).isEqualTo(otro.hashCode());
    }
}
