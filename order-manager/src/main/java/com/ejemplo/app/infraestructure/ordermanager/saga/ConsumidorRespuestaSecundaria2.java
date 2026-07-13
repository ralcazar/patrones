package com.ejemplo.app.infraestructure.ordermanager.saga;

import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.stereotype.Component;

import com.ejemplo.app.business.ordermanager.aplicacion.puerto.salida.PuertoColaTareas;
import com.ejemplo.app.business.ordermanager.aplicacion.tarea.TareaSaga;
import com.ejemplo.app.business.ordermanager.dominio.comun.SagaId;
import com.ejemplo.app.business.ordermanager.dominio.sagasecundaria2.RefRespuesta;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Consumer FINO del topic de respuesta de la saga secundaria 2 (el único uso
 * de Kafka en la aplicación): no procesa la saga, solo traduce el evento a
 * una tarea y la encola. Ventajas:
 *  - El commit del offset de Kafka y el insert de la orden son casi
 *    instantáneos: sin rebalanceos por procesamiento lento.
 *  - Todo el trabajo de saga pasa por el mismo pool con lease e idempotencia.
 *  - Si Kafka reentrega, se insertan dos órdenes; la deduplicación por
 *    mensajeId hace la segunda inofensiva.
 * Ajusta el parseo del evento al esquema real de tu topic.
 */
@Component
public class ConsumidorRespuestaSecundaria2 {

    private final PuertoColaTareas cola;
    private final ObjectMapper mapper = new ObjectMapper();

    public ConsumidorRespuestaSecundaria2(PuertoColaTareas cola) {
        this.cola = cola;
    }

    @KafkaListener(topics = "${ordermanager.topics.respuesta-secundaria2:respuesta.secundaria2}")
    public void onRespuesta(String mensaje,
                            @Header(name = "kafka_receivedMessageKey", required = false) String key) throws Exception {
        JsonNode n = mapper.readTree(mensaje);
        var sagaId = SagaId.de(n.get("sagaId").asText());   // clave de correlación puesta por PuertoSagaSecundaria2
        var mensajeId = n.get("mensajeId").asText();        // id único del evento (dedup)

        if (n.get("exito").asBoolean()) {
            cola.encolar(new TareaSaga.ResultadoSagaSecundaria2Ok(sagaId,
                    new RefRespuesta(n.get("ref").asText()), mensajeId));
        } else {
            cola.encolar(new TareaSaga.ResultadoSagaSecundaria2Error(sagaId,
                    n.get("codigo").asText(), n.get("detalle").asText(),
                    n.path("reintentable").asBoolean(true), mensajeId));
        }
    }
}
