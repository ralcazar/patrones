package com.ejemplo.app.business.sagas.dominio.sagasecundaria1;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import com.ejemplo.app.business.ordermanager.dominio.DatosManualesRequeridosException;
import com.ejemplo.app.business.ordermanager.dominio.ExternalId;
import com.ejemplo.app.business.ordermanager.dominio.OrdenId;
import com.ejemplo.app.business.ordermanager.dominio.PasoNoIntervenibleException;
import com.ejemplo.app.business.ordermanager.dominio.UsuarioSoporte;
import com.ejemplo.app.business.sagas.dominio.comun.ContextoArranque;
import com.ejemplo.app.business.sagas.dominio.comun.RefPaso1;
import com.ejemplo.app.business.sagas.dominio.sagasecundaria3.RefEjecucion;
import com.ejemplo.app.business.sagas.dominio.sagasecundaria3.ResultadoPasoSecundaria3;

/**
 * Saga secundaria 1: INICIAL -&gt; INICIO_HECHO -&gt; TERMINADA, dos pasos síncronos
 * encadenados donde CONFIRMACION consume la referencia que produce INICIO.
 *
 * {@code SagaSecundaria1} es un value object inmutable: cada transición
 * devuelve una instancia nueva, así que los tests reasignan la variable
 * local tras cada llamada en vez de mutar "in place".
 */
class SagaSecundaria1Test {

    private static SagaSecundaria1 nueva() {
        var ctx = new ContextoArranque.ArranqueSecundaria1(
                ExternalId.de(UUID.randomUUID().toString()), new RefPaso1("ref1"));
        return SagaSecundaria1.crear(OrdenId.nuevo(), ctx);
    }

    @Test
    void crear_arrancaEnInicialConComandoIniciar() {
        var saga = nueva();

        assertThat(saga.estado()).isEqualTo(EstadoSagaSecundaria1.INICIAL);
        assertThat(saga.comandoActual()).isInstanceOf(ComandoPasoSecundaria1.Iniciar.class);
    }

    @Test
    void aplicarYAvanzar_iniciada_pasaAInicioHechoConComandoConfirmar() {
        var saga = nueva();

        saga = saga.aplicarYAvanzar(new ResultadoPasoSecundaria1.Iniciada(new RefInicio("ini1")));

        assertThat(saga.estado()).isEqualTo(EstadoSagaSecundaria1.INICIO_HECHO);
        assertThat(saga.refInicio().valor()).isEqualTo("ini1");
        assertThat(saga.comandoActual()).isInstanceOf(ComandoPasoSecundaria1.Confirmar.class);
    }

    @Test
    void aplicarYAvanzar_confirmada_terminaLaSaga() {
        var saga = nueva();
        saga = saga.aplicarYAvanzar(new ResultadoPasoSecundaria1.Iniciada(new RefInicio("ini1")));

        saga = saga.aplicarYAvanzar(new ResultadoPasoSecundaria1.Confirmada(new RefConfirmacion("conf1")));

        assertThat(saga.estado()).isEqualTo(EstadoSagaSecundaria1.TERMINADA);
        assertThat(saga.terminada()).isTrue();
        assertThat(saga.refConfirmacion().valor()).isEqualTo("conf1");
    }

    @Test
    void comandoActual_enTerminadaNoTienePasoPendiente() {
        var saga = nueva();
        saga = saga.aplicarYAvanzar(new ResultadoPasoSecundaria1.Iniciada(new RefInicio("ini1")));
        saga = saga.aplicarYAvanzar(new ResultadoPasoSecundaria1.Confirmada(new RefConfirmacion("conf1")));

        assertThatThrownBy(saga::comandoActual).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void aplicarYAvanzar_conResultadoDeOtraSaga_lanzaIllegalArgumentException() {
        var saga = nueva();

        assertThatThrownBy(() -> saga.aplicarYAvanzar(
                new ResultadoPasoSecundaria3.Ejecutada(new RefEjecucion("x"))))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void aplicarYAvanzar_enTerminada_propagaIllegalStateExceptionDeAvanzar() {
        var saga = nueva();
        saga = saga.aplicarYAvanzar(new ResultadoPasoSecundaria1.Iniciada(new RefInicio("ini1")));
        saga = saga.aplicarYAvanzar(new ResultadoPasoSecundaria1.Confirmada(new RefConfirmacion("conf1")));
        var terminada = saga;

        assertThatThrownBy(() -> terminada.aplicarYAvanzar(new ResultadoPasoSecundaria1.Confirmada(new RefConfirmacion("otra"))))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void marcarPasoActualOkManual_enInicialConDatos_aplicaResultadoYAvanzaAInicioHecho() {
        var saga = nueva();

        saga = saga.marcarPasoActualOkManual(new UsuarioSoporte("ana"), "motivo", Map.of("refInicio", "ini-manual"));

        assertThat(saga.estado()).isEqualTo(EstadoSagaSecundaria1.INICIO_HECHO);
        assertThat(saga.refInicio().valor()).isEqualTo("ini-manual");
    }

    @Test
    void marcarPasoActualOkManual_enInicialSinDatos_lanzaDatosManualesRequeridosException() {
        var saga = nueva();

        assertThatThrownBy(() -> saga.marcarPasoActualOkManual(new UsuarioSoporte("ana"), "motivo", null))
                .isInstanceOf(DatosManualesRequeridosException.class);
    }

    @Test
    void marcarPasoActualOkManual_enInicialConDatosVacios_lanzaDatosManualesRequeridosException() {
        var saga = nueva();

        assertThatThrownBy(() -> saga.marcarPasoActualOkManual(new UsuarioSoporte("ana"), "motivo", Map.of()))
                .isInstanceOf(DatosManualesRequeridosException.class);
    }

    @Test
    void marcarPasoActualOkManual_faltandoLaClaveObligatoria_lanzaIllegalArgumentException() {
        var saga = nueva();

        assertThatThrownBy(() -> saga.marcarPasoActualOkManual(new UsuarioSoporte("ana"), "motivo", Map.of("otra", "x")))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void marcarPasoActualOkManual_enInicioHecho_avanzaATerminadaSinAplicarResultado() {
        var saga = nueva();
        saga = saga.aplicarYAvanzar(new ResultadoPasoSecundaria1.Iniciada(new RefInicio("ini1")));

        saga = saga.marcarPasoActualOkManual(new UsuarioSoporte("ana"), "motivo", null);

        assertThat(saga.estado()).isEqualTo(EstadoSagaSecundaria1.TERMINADA);
        assertThat(saga.refConfirmacion()).isNull();
    }

    @Test
    void marcarPasoActualOkManual_enTerminada_lanzaPasoNoIntervenibleException() {
        var saga = nueva();
        saga = saga.aplicarYAvanzar(new ResultadoPasoSecundaria1.Iniciada(new RefInicio("ini1")));
        saga = saga.aplicarYAvanzar(new ResultadoPasoSecundaria1.Confirmada(new RefConfirmacion("conf1")));
        var terminada = saga;

        assertThatThrownBy(() -> terminada.marcarPasoActualOkManual(new UsuarioSoporte("ana"), "motivo", null))
                .isInstanceOf(PasoNoIntervenibleException.class);
    }

    @Test
    void marcarPasoActualOkManual_enInicioHechoConDatosVacios_avanzaSinAplicarResultado() {
        var saga = nueva();
        saga = saga.aplicarYAvanzar(new ResultadoPasoSecundaria1.Iniciada(new RefInicio("ini1")));

        saga = saga.marcarPasoActualOkManual(new UsuarioSoporte("ana"), "motivo", Map.of());

        assertThat(saga.estado()).isEqualTo(EstadoSagaSecundaria1.TERMINADA);
    }

    @Test
    void marcarPasoActualOkManual_enInicioHechoConDatosNoVacios_seIgnoranYAvanzaATerminada() {
        var saga = nueva();
        saga = saga.aplicarYAvanzar(new ResultadoPasoSecundaria1.Iniciada(new RefInicio("ini1")));

        saga = saga.marcarPasoActualOkManual(new UsuarioSoporte("ana"), "motivo", Map.of("dato", "ignorado"));

        assertThat(saga.estado()).isEqualTo(EstadoSagaSecundaria1.TERMINADA);
        assertThat(saga.refConfirmacion()).isNull();
    }

    @Test
    void marcarPasoActualOkManual_conValorEnBlancoEnLaClaveObligatoria_lanzaIllegalArgumentException() {
        var saga = nueva();

        assertThatThrownBy(() -> saga.marcarPasoActualOkManual(new UsuarioSoporte("ana"), "motivo", Map.of("refInicio", " ")))
                .isInstanceOf(IllegalArgumentException.class);
    }

    // ------------------------------------------------------------------
    // equals/hashCode: value object -- igualdad por (id, externalId, estado,
    // auditoria) heredados de Proceso, más refPaso1/refInicio/refConfirmacion.
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
        var secundaria1 = SagaSecundaria1.rehidratar(id, externalId, new RefPaso1("ref1"), null, null,
                EstadoSagaSecundaria1.INICIAL, java.util.List.of());
        var secundaria3 = com.ejemplo.app.business.sagas.dominio.sagasecundaria3.SagaSecundaria3.rehidratar(
                id, externalId, new com.ejemplo.app.business.sagas.dominio.comun.RefPaso7("ref7"), null,
                com.ejemplo.app.business.sagas.dominio.sagasecundaria3.EstadoSagaSecundaria3.INICIAL, java.util.List.of());

        assertThat(secundaria1.equals(secundaria3)).isFalse();
    }

    @Test
    void equals_conRefPaso1Distinto_esFalse() {
        var id = OrdenId.nuevo();
        var externalId = ExternalId.de(UUID.randomUUID().toString());
        var uno = SagaSecundaria1.rehidratar(id, externalId, new RefPaso1("ref1"), null, null,
                EstadoSagaSecundaria1.INICIAL, java.util.List.of());
        var otro = SagaSecundaria1.rehidratar(id, externalId, new RefPaso1("otro-ref1"), null, null,
                EstadoSagaSecundaria1.INICIAL, java.util.List.of());

        assertThat(uno.equals(otro)).isFalse();
    }

    @Test
    void equals_conRefInicioDistinta_esFalse() {
        var id = OrdenId.nuevo();
        var externalId = ExternalId.de(UUID.randomUUID().toString());
        var uno = SagaSecundaria1.rehidratar(id, externalId, new RefPaso1("ref1"), new RefInicio("ini1"), null,
                EstadoSagaSecundaria1.INICIO_HECHO, java.util.List.of());
        var otro = SagaSecundaria1.rehidratar(id, externalId, new RefPaso1("ref1"), new RefInicio("ini2"), null,
                EstadoSagaSecundaria1.INICIO_HECHO, java.util.List.of());

        assertThat(uno.equals(otro)).isFalse();
    }

    @Test
    void equals_conRefConfirmacionDistinta_esFalse() {
        var id = OrdenId.nuevo();
        var externalId = ExternalId.de(UUID.randomUUID().toString());
        var uno = SagaSecundaria1.rehidratar(id, externalId, new RefPaso1("ref1"), new RefInicio("ini1"),
                new RefConfirmacion("conf1"), EstadoSagaSecundaria1.TERMINADA, java.util.List.of());
        var otro = SagaSecundaria1.rehidratar(id, externalId, new RefPaso1("ref1"), new RefInicio("ini1"),
                new RefConfirmacion("conf2"), EstadoSagaSecundaria1.TERMINADA, java.util.List.of());

        assertThat(uno.equals(otro)).isFalse();
    }

    @Test
    void equals_conTodosLosCamposIguales_esTrue() {
        var id = OrdenId.nuevo();
        var externalId = ExternalId.de(UUID.randomUUID().toString());
        var uno = SagaSecundaria1.rehidratar(id, externalId, new RefPaso1("ref1"), new RefInicio("ini1"),
                new RefConfirmacion("conf1"), EstadoSagaSecundaria1.TERMINADA, java.util.List.of());
        var otro = SagaSecundaria1.rehidratar(id, externalId, new RefPaso1("ref1"), new RefInicio("ini1"),
                new RefConfirmacion("conf1"), EstadoSagaSecundaria1.TERMINADA, java.util.List.of());

        assertThat(uno).isEqualTo(otro);
        assertThat(uno.hashCode()).isEqualTo(otro.hashCode());
    }
}
