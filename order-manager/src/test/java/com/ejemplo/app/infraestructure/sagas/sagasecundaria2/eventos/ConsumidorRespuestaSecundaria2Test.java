package com.ejemplo.app.infraestructure.sagas.sagasecundaria2.eventos;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.UUID;

import org.junit.jupiter.api.Test;

import com.ejemplo.app.business.ordermanager.dominio.OrdenId;
import com.ejemplo.app.business.sagas.aplicacion.puerto.entrada.CasoUsoRegistrarRespuestaSecundaria2;
import com.ejemplo.app.business.sagas.dominio.sagasecundaria2.RefRespuesta;

/**
 * Consumer fino: solo parsea el JSON del evento y delega en el caso de uso.
 * Se prueba llamando directamente a {@code onRespuesta}, sin Kafka.
 */
class ConsumidorRespuestaSecundaria2Test {

    private static class RegistroFalso implements CasoUsoRegistrarRespuestaSecundaria2 {

        OrdenId sagaIdOk;
        RefRespuesta refOk;
        String mensajeIdOk;

        OrdenId sagaIdError;
        String codigoError;
        String detalleError;
        boolean reintentableError;
        String mensajeIdError;

        @Override
        public void respuestaOk(OrdenId sagaId, RefRespuesta ref, String mensajeId) {
            this.sagaIdOk = sagaId;
            this.refOk = ref;
            this.mensajeIdOk = mensajeId;
        }

        @Override
        public void respuestaError(OrdenId sagaId, String codigo, String detalle, boolean reintentable,
                String mensajeId) {
            this.sagaIdError = sagaId;
            this.codigoError = codigo;
            this.detalleError = detalle;
            this.reintentableError = reintentable;
            this.mensajeIdError = mensajeId;
        }
    }

    @Test
    void onRespuesta_conExitoTrueDelegaEnRespuestaOk() throws Exception {
        var registro = new RegistroFalso();
        var consumer = new ConsumidorRespuestaSecundaria2(registro, "pod-test");
        var id = UUID.randomUUID();
        var mensaje = """
                {"sagaId":"%s","mensajeId":"msg-1","exito":true,"ref":"ref-solicitud"}
                """.formatted(id);

        consumer.onRespuesta(mensaje, "clave-kafka");

        assertThat(registro.sagaIdOk).isEqualTo(OrdenId.de(id.toString()));
        assertThat(registro.refOk).isEqualTo(new RefRespuesta("ref-solicitud"));
        assertThat(registro.mensajeIdOk).isEqualTo("msg-1");
        assertThat(registro.sagaIdError).isNull();
    }

    @Test
    void onRespuesta_conExitoFalseYReintentableExplicitoDelegaEnRespuestaError() throws Exception {
        var registro = new RegistroFalso();
        var consumer = new ConsumidorRespuestaSecundaria2(registro, "pod-test");
        var id = UUID.randomUUID();
        var mensaje = """
                {"sagaId":"%s","mensajeId":"msg-2","exito":false,"codigo":"ERR-1","detalle":"fallo remoto","reintentable":false}
                """.formatted(id);

        consumer.onRespuesta(mensaje, null);

        assertThat(registro.sagaIdError).isEqualTo(OrdenId.de(id.toString()));
        assertThat(registro.codigoError).isEqualTo("ERR-1");
        assertThat(registro.detalleError).isEqualTo("fallo remoto");
        assertThat(registro.reintentableError).isFalse();
        assertThat(registro.mensajeIdError).isEqualTo("msg-2");
        assertThat(registro.sagaIdOk).isNull();
    }

    @Test
    void onRespuesta_conExitoFalseSinCampoReintentableUsaTrueComoDefecto() throws Exception {
        var registro = new RegistroFalso();
        var consumer = new ConsumidorRespuestaSecundaria2(registro, "pod-test");
        var id = UUID.randomUUID();
        var mensaje = """
                {"sagaId":"%s","mensajeId":"msg-3","exito":false,"codigo":"ERR-2","detalle":"timeout"}
                """.formatted(id);

        consumer.onRespuesta(mensaje, "clave-kafka");

        assertThat(registro.reintentableError).isTrue();
    }
}
