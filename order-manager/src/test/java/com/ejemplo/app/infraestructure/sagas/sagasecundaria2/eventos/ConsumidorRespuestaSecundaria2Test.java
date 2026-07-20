package com.ejemplo.app.infraestructure.sagas.sagasecundaria2.eventos;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.UUID;

import org.junit.jupiter.api.Test;

import com.ejemplo.app.business.ordermanager.dominio.OrdenId;
import com.ejemplo.app.business.sagas.aplicacion.puerto.entrada.CasoUsoRegistrarRespuestaSecundaria2;
import com.ejemplo.app.business.sagas.dominio.sagasecundaria2.RefRespuesta;

/**
 * Consumer fino: solo parsea el JSON del evento (que siempre trae éxito, no
 * hay caso de error en el contrato) y delega en el caso de uso. Se prueba
 * llamando directamente a {@code onRespuesta}, sin Kafka.
 */
class ConsumidorRespuestaSecundaria2Test {

    private static class RegistroFalso implements CasoUsoRegistrarRespuestaSecundaria2 {

        OrdenId sagaIdOk;
        RefRespuesta refOk;

        @Override
        public void respuestaOk(OrdenId sagaId, RefRespuesta ref) {
            this.sagaIdOk = sagaId;
            this.refOk = ref;
        }
    }

    @Test
    void onRespuesta_delegaEnRespuestaOk() throws Exception {
        var registro = new RegistroFalso();
        var consumer = new ConsumidorRespuestaSecundaria2(registro, "pod-test");
        var id = UUID.randomUUID();
        var mensaje = """
                {"sagaId":"%s","mensajeId":"msg-1","exito":true,"ref":"ref-solicitud"}
                """.formatted(id);

        consumer.onRespuesta(mensaje, "clave-kafka");

        assertThat(registro.sagaIdOk).isEqualTo(OrdenId.de(id.toString()));
        assertThat(registro.refOk).isEqualTo(new RefRespuesta("ref-solicitud"));
    }
}
