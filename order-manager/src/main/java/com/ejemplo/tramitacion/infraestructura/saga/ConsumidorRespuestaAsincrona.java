package com.ejemplo.tramitacion.infraestructura.saga;

import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.stereotype.Component;

import com.ejemplo.tramitacion.aplicacion.saga.puerto.salida.PuertoColaTareas;
import com.ejemplo.tramitacion.aplicacion.saga.tarea.TareaSaga;
import com.ejemplo.tramitacion.dominio.saga.asincrono.RefAsincrono;
import com.ejemplo.tramitacion.dominio.saga.general.SagaId;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Consumer FINO del topic de respuesta del paso ASINCRONO: no procesa la saga,
 * solo traduce el evento a una tarea y la encola. Ventajas:
 *  - El commit del offset de Kafka y el insert de la orden son casi
 *    instantáneos: sin rebalanceos por procesamiento lento.
 *  - Todo el trabajo de saga pasa por el mismo pool con lease e idempotencia.
 *  - Si Kafka reentrega, se insertan dos órdenes; la deduplicación por
 *    mensajeId hace la segunda inofensiva.
 * Ajusta el parseo del evento al esquema real de tu topic.
 */
@Component
public class ConsumidorRespuestaAsincrona {

    private final PuertoColaTareas cola;
    private final ObjectMapper mapper = new ObjectMapper();

    public ConsumidorRespuestaAsincrona(PuertoColaTareas cola) {
        this.cola = cola;
    }

    @KafkaListener(topics = "${tramitacion.topics.respuesta-asincrona:respuesta.asincrona}")
    public void onRespuesta(String mensaje,
                            @Header(name = "kafka_receivedMessageKey", required = false) String key) throws Exception {
        JsonNode n = mapper.readTree(mensaje);
        var sagaId = SagaId.de(n.get("sagaId").asText());   // clave de correlación puesta por PuertoAsincrono
        var mensajeId = n.get("mensajeId").asText();        // id único del evento (dedup)

        if (n.get("exito").asBoolean()) {
            cola.encolar(new TareaSaga.ResultadoAsincronoOk(sagaId,
                    new RefAsincrono(n.get("ref").asText()), mensajeId));
        } else {
            cola.encolar(new TareaSaga.ResultadoAsincronoError(sagaId,
                    n.get("codigo").asText(), n.get("detalle").asText(),
                    n.path("reintentable").asBoolean(true), mensajeId));
        }
    }
}
