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
import com.ejemplo.app.business.ordermanager.dominio.ResultadoOrden;
import com.ejemplo.app.business.ordermanager.dominio.OrdenId;
import com.ejemplo.app.business.ordermanager.dominio.UsuarioSoporte;

/**
 * Saga secundaria 2: INICIAL -&gt; ESPERANDO_RESPUESTA -&gt; TERMINADA, con la
 * posibilidad de volver a INICIAL si la conciliación detecta un fallo
 * registrado en destino.
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

        saga.solicitudEnviada();

        assertThat(saga.estado()).isEqualTo(EstadoSagaSecundaria2.ESPERANDO_RESPUESTA);
        assertThatThrownBy(saga::comandoActual).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void respuestaRecibida_terminaLaSagaConElResultadoOk() {
        var saga = nueva();
        saga.solicitudEnviada();

        saga.respuestaRecibida(new RefRespuesta("resp-1"));

        assertThat(saga.estado()).isEqualTo(EstadoSagaSecundaria2.TERMINADA);
        assertThat(saga.terminada()).isTrue();
        assertThat(saga.refRespuesta().valor()).isEqualTo("resp-1");
        assertThat(saga.resultadoFinal()).isEqualTo(ResultadoOrden.FINALIZADA_OK);
    }

    @Test
    void volverASolicitar_desdeEsperandoRespuestaVuelveAInicial() {
        var saga = nueva();
        saga.solicitudEnviada();

        saga.volverASolicitar();

        assertThat(saga.estado()).isEqualTo(EstadoSagaSecundaria2.INICIAL);
        assertThat(saga.comandoActual()).isInstanceOf(ComandoPasoSecundaria2.Solicitar.class);
    }

    @Test
    void marcarPasoActualOkManual_enTerminadaNoEsIntervenible() {
        var saga = nueva();
        saga.solicitudEnviada();
        saga.respuestaRecibida(new RefRespuesta("resp-1"));

        assertThatThrownBy(() -> saga.marcarPasoActualOkManual(new UsuarioSoporte("ana"), "motivo", null))
                .isInstanceOf(PasoNoIntervenibleException.class);
    }

    @Test
    void marcarPasoActualOkManual_enInicialTerminaLaSagaManualmente() {
        var saga = nueva();

        saga.marcarPasoActualOkManual(new UsuarioSoporte("ana"), "motivo", null);

        assertThat(saga.estado()).isEqualTo(EstadoSagaSecundaria2.TERMINADA);
    }

    @Test
    void marcarPasoActualOkManual_enEsperandoRespuestaTerminaLaSagaManualmente() {
        var saga = nueva();
        saga.solicitudEnviada();

        saga.marcarPasoActualOkManual(new UsuarioSoporte("ana"), "motivo", null);

        assertThat(saga.estado()).isEqualTo(EstadoSagaSecundaria2.TERMINADA);
    }

    @Test
    void solicitudEnviada_fueraDeInicial_lanzaIllegalStateException() {
        var saga = nueva();
        saga.solicitudEnviada();

        assertThatThrownBy(saga::solicitudEnviada).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void aplicarYAvanzar_conRespuesta_terminaLaSagaConLaRef() {
        var saga = nueva();

        saga.aplicarYAvanzar(new ResultadoPasoSecundaria2.Respuesta(new RefRespuesta("resp-2")));

        assertThat(saga.estado()).isEqualTo(EstadoSagaSecundaria2.TERMINADA);
        assertThat(saga.refRespuesta().valor()).isEqualTo("resp-2");
    }

    @Test
    void aplicarYAvanzar_conResultadoDeOtraSaga_lanzaIllegalArgumentException() {
        var saga = nueva();

        assertThatThrownBy(() -> saga.aplicarYAvanzar(new ResultadoPasoSecundaria1.Iniciada(new RefInicio("x"))))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
