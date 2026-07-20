package com.ejemplo.app.business.sagas.dominio.sagasecundaria2;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.UUID;

import org.junit.jupiter.api.Test;

import com.ejemplo.app.business.sagas.dominio.comun.ContextoArranque;
import com.ejemplo.app.business.ordermanager.dominio.ExternalId;
import com.ejemplo.app.business.ordermanager.dominio.PasoNoIntervenibleException;
import com.ejemplo.app.business.sagas.dominio.comun.RefPaso5;
import com.ejemplo.app.business.sagas.dominio.sagasecundaria1.RefInicio;
import com.ejemplo.app.business.sagas.dominio.sagasecundaria1.ResultadoPasoSecundaria1;
import com.ejemplo.app.business.ordermanager.dominio.OrdenId;
import com.ejemplo.app.business.ordermanager.dominio.UsuarioSoporte;

/**
 * Saga secundaria 2: INICIAL -&gt; ESPERANDO_RESPUESTA -&gt; TERMINADA, con la
 * posibilidad de volver a INICIAL si la conciliación detecta un fallo
 * registrado en destino.
 *
 * {@code SagaSecundaria2} es un value object inmutable: cada transición
 * devuelve una instancia nueva, así que los tests reasignan la variable
 * local tras cada llamada en vez de mutar "in place".
 */
class SagaSecundaria2Test {

    private static SagaSecundaria2 nueva() {
        var ctx = new ContextoArranque.ArranqueSecundaria2(
                ExternalId.de(UUID.randomUUID().toString()), new RefPaso5("ref5"));
        return SagaSecundaria2.crear(OrdenId.nuevo(), ctx);
    }

    @Test
    void crear_arrancaEnInicialConComandoSolicitar() {
        var saga = nueva();

        assertThat(saga.estado()).isEqualTo(EstadoSagaSecundaria2.INICIAL);
        assertThat(saga.comandoActual()).isInstanceOf(ComandoPasoSecundaria2.Solicitar.class);
        assertThat(saga.terminada()).isFalse();
    }

    @Test
    void solicitudEnviada_pasaAEsperandoRespuestaYYaNoTienePasoPendiente() {
        var saga = nueva();

        saga = saga.solicitudEnviada();

        assertThat(saga.estado()).isEqualTo(EstadoSagaSecundaria2.ESPERANDO_RESPUESTA);
        assertThatThrownBy(saga::comandoActual).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void respuestaRecibida_terminaLaSagaConElResultadoOk() {
        var saga = nueva();
        saga = saga.solicitudEnviada();

        saga = saga.respuestaRecibida(new RefRespuesta("resp-1"));

        assertThat(saga.estado()).isEqualTo(EstadoSagaSecundaria2.TERMINADA);
        assertThat(saga.terminada()).isTrue();
        assertThat(saga.refRespuesta().valor()).isEqualTo("resp-1");
    }

    @Test
    void marcarPasoActualOkManual_enTerminadaNoEsIntervenible() {
        var saga = nueva();
        saga = saga.solicitudEnviada();
        saga = saga.respuestaRecibida(new RefRespuesta("resp-1"));
        var terminada = saga;

        assertThatThrownBy(() -> terminada.marcarPasoActualOkManual(new UsuarioSoporte("ana"), "motivo", null))
                .isInstanceOf(PasoNoIntervenibleException.class);
    }

    @Test
    void marcarPasoActualOkManual_enInicialTerminaLaSagaManualmente() {
        var saga = nueva();

        saga = saga.marcarPasoActualOkManual(new UsuarioSoporte("ana"), "motivo", null);

        assertThat(saga.estado()).isEqualTo(EstadoSagaSecundaria2.TERMINADA);
    }

    @Test
    void marcarPasoActualOkManual_enEsperandoRespuestaTerminaLaSagaManualmente() {
        var saga = nueva();
        saga = saga.solicitudEnviada();

        saga = saga.marcarPasoActualOkManual(new UsuarioSoporte("ana"), "motivo", null);

        assertThat(saga.estado()).isEqualTo(EstadoSagaSecundaria2.TERMINADA);
    }

    @Test
    void solicitudEnviada_fueraDeInicial_lanzaIllegalStateException() {
        var saga = nueva();
        saga = saga.solicitudEnviada();
        var esperandoRespuesta = saga;

        assertThatThrownBy(esperandoRespuesta::solicitudEnviada).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void aplicarYAvanzar_conRespuesta_terminaLaSagaConLaRef() {
        var saga = nueva();

        saga = saga.aplicarYAvanzar(new ResultadoPasoSecundaria2.Respuesta(new RefRespuesta("resp-2")));

        assertThat(saga.estado()).isEqualTo(EstadoSagaSecundaria2.TERMINADA);
        assertThat(saga.refRespuesta().valor()).isEqualTo("resp-2");
    }

    @Test
    void aplicarYAvanzar_conResultadoDeOtraSaga_lanzaIllegalArgumentException() {
        var saga = nueva();

        assertThatThrownBy(() -> saga.aplicarYAvanzar(new ResultadoPasoSecundaria1.Iniciada(new RefInicio("x"))))
                .isInstanceOf(IllegalArgumentException.class);
    }

    // ------------------------------------------------------------------
    // equals/hashCode: value object -- igualdad por (id, externalId, estado,
    // auditoria) heredados de Proceso, más refPaso5/refRespuesta.
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
        var secundaria2 = SagaSecundaria2.rehidratar(id, externalId, new RefPaso5("ref5"), null,
                EstadoSagaSecundaria2.INICIAL, java.util.List.of());
        var secundaria3 = com.ejemplo.app.business.sagas.dominio.sagasecundaria3.SagaSecundaria3.rehidratar(
                id, externalId, new com.ejemplo.app.business.sagas.dominio.comun.RefPaso7("ref7"), null,
                com.ejemplo.app.business.sagas.dominio.sagasecundaria3.EstadoSagaSecundaria3.INICIAL, java.util.List.of());

        assertThat(secundaria2.equals(secundaria3)).isFalse();
    }

    @Test
    void equals_conRefPaso5Distinta_esFalse() {
        var id = OrdenId.nuevo();
        var externalId = ExternalId.de(UUID.randomUUID().toString());
        var uno = SagaSecundaria2.rehidratar(id, externalId, new RefPaso5("ref5"), null,
                EstadoSagaSecundaria2.INICIAL, java.util.List.of());
        var otro = SagaSecundaria2.rehidratar(id, externalId, new RefPaso5("otro-ref5"), null,
                EstadoSagaSecundaria2.INICIAL, java.util.List.of());

        assertThat(uno.equals(otro)).isFalse();
    }

    @Test
    void equals_conRefRespuestaDistinta_esFalse() {
        var id = OrdenId.nuevo();
        var externalId = ExternalId.de(UUID.randomUUID().toString());
        var uno = SagaSecundaria2.rehidratar(id, externalId, new RefPaso5("ref5"), new RefRespuesta("resp1"),
                EstadoSagaSecundaria2.TERMINADA, java.util.List.of());
        var otro = SagaSecundaria2.rehidratar(id, externalId, new RefPaso5("ref5"), new RefRespuesta("resp2"),
                EstadoSagaSecundaria2.TERMINADA, java.util.List.of());

        assertThat(uno.equals(otro)).isFalse();
    }

    @Test
    void equals_conTodosLosCamposIguales_esTrue() {
        var id = OrdenId.nuevo();
        var externalId = ExternalId.de(UUID.randomUUID().toString());
        var uno = SagaSecundaria2.rehidratar(id, externalId, new RefPaso5("ref5"), new RefRespuesta("resp1"),
                EstadoSagaSecundaria2.TERMINADA, java.util.List.of());
        var otro = SagaSecundaria2.rehidratar(id, externalId, new RefPaso5("ref5"), new RefRespuesta("resp1"),
                EstadoSagaSecundaria2.TERMINADA, java.util.List.of());

        assertThat(uno).isEqualTo(otro);
        assertThat(uno.hashCode()).isEqualTo(otro.hashCode());
    }
}
