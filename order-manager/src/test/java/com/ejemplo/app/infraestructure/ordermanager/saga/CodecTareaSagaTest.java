package com.ejemplo.app.infraestructure.ordermanager.saga;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.UUID;

import org.junit.jupiter.api.Test;

import com.ejemplo.app.business.ordermanager.aplicacion.tarea.TareaSaga;
import com.ejemplo.app.business.ordermanager.dominio.comun.ExternalId;
import com.ejemplo.app.business.ordermanager.dominio.comun.SagaId;
import com.ejemplo.app.business.ordermanager.dominio.comun.TipoSaga;
import com.ejemplo.app.business.ordermanager.dominio.sagaprincipal.DatoNegocio2;
import com.ejemplo.app.business.ordermanager.dominio.sagaprincipal.DatoNegocio3;
import com.ejemplo.app.business.ordermanager.dominio.sagaprincipal.PasoSagaPrincipal;
import com.ejemplo.app.business.ordermanager.dominio.sagasecundaria1.PasoSagaSecundaria1;
import com.ejemplo.app.business.ordermanager.dominio.sagasecundaria2.RefRespuesta;

/**
 * Round-trip de las 6 tareas: el contenido persiste en BBDD y el formato
 * (discriminadores, paso por nombre, tipoSaga en ARRANCAR_SAGA) es el contrato.
 */
class CodecTareaSagaTest {

    private final CodecTareaSaga codec = new CodecTareaSaga();
    private final SagaId sagaId = SagaId.nuevo();

    private TareaSaga idaYVuelta(TareaSaga tarea) {
        return codec.decodificar(codec.codificar(tarea));
    }

    @Test
    void iniciarTramitacion() {
        var tarea = new TareaSaga.IniciarTramitacion(sagaId, new ExternalId(UUID.randomUUID()),
                new DatoNegocio3("a", "b"), new DatoNegocio2("c", "d"));
        assertThat(codec.tipoDe(tarea)).isEqualTo("INICIAR");
        assertThat(idaYVuelta(tarea)).isEqualTo(tarea);
    }

    @Test
    void arrancarSagaLlevaElTipo() {
        var tarea = new TareaSaga.ArrancarSaga(sagaId, TipoSaga.SECUNDARIA2);
        assertThat(codec.tipoDe(tarea)).isEqualTo("ARRANCAR_SAGA");
        assertThat(idaYVuelta(tarea)).isEqualTo(tarea);
    }

    @Test
    void reintentarReconstruyeElPasoPorNombre() {
        var principal = new TareaSaga.Reintentar(TipoSaga.PRINCIPAL, sagaId, PasoSagaPrincipal.PASO5, 3);
        var secundaria = new TareaSaga.Reintentar(TipoSaga.SECUNDARIA1, sagaId, PasoSagaSecundaria1.CONFIRMACION, 1);
        assertThat(idaYVuelta(principal)).isEqualTo(principal);
        assertThat(idaYVuelta(secundaria)).isEqualTo(secundaria);
    }

    @Test
    void timeoutSagaSecundaria2() {
        var tarea = new TareaSaga.TimeoutSagaSecundaria2(sagaId);
        assertThat(codec.tipoDe(tarea)).isEqualTo("TIMEOUT_SECUNDARIA2");
        assertThat(idaYVuelta(tarea)).isEqualTo(tarea);
    }

    @Test
    void resultadoSagaSecundaria2OkYError() {
        var ok = new TareaSaga.ResultadoSagaSecundaria2Ok(sagaId, new RefRespuesta("ref-1"), "msg-1");
        var error = new TareaSaga.ResultadoSagaSecundaria2Error(sagaId, "COD", "detalle", true, "msg-2");
        assertThat(codec.tipoDe(ok)).isEqualTo("RESULTADO_SECUNDARIA2_OK");
        assertThat(codec.tipoDe(error)).isEqualTo("RESULTADO_SECUNDARIA2_ERROR");
        assertThat(idaYVuelta(ok)).isEqualTo(ok);
        assertThat(idaYVuelta(error)).isEqualTo(error);
    }
}
