package com.ejemplo.app.infraestructure.sagas.eventos;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.stereotype.Component;

import com.ejemplo.app.business.sagas.aplicacion.puerto.entrada.CasoUsoRegistrarRespuestaSecundaria2;
import com.ejemplo.app.business.ordermanager.dominio.OrdenId;
import com.ejemplo.app.business.sagas.dominio.sagasecundaria2.RefRespuesta;
import com.ejemplo.app.business.sagas.dominio.sagasecundaria2.SagaSecundaria2;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Consumer FINO del topic de respuesta de la saga secundaria 2 (el único uso
 * de Kafka en la aplicación): no procesa la saga, solo parsea el evento y lo
 * entrega al caso de uso de registro, que lo encola como tarea. Ventajas:
 *  - El commit del offset de Kafka y el insert de la orden son casi
 *    instantáneos: sin rebalanceos por procesamiento lento.
 *  - Todo el trabajo de saga pasa por el mismo pool con lease e idempotencia.
 *  - Si Kafka reentrega, se insertan dos órdenes; la deduplicación por
 *    mensajeId hace la segunda inofensiva.
 * Como adaptador de entrada, no toca la cola (adaptador de salida): pasa por
 * la capa de aplicación (regla de arquitectura del CLAUDE.md).
 * Ajusta el parseo del evento al esquema real de tu topic.
 *
 * El registro de la respuesta nace ya en este adaptador de entrada: se
 * loguea aquí mismo, con el mismo formato que {@code PuertoObservadorEjecucion}
 * (ver catálogo de eventos en {@code src/pruebaCarga/resources/escenarios/README.md}).
 */
@Component
public class ConsumidorRespuestaSecundaria2 {

    private static final Logger log = LoggerFactory.getLogger(ConsumidorRespuestaSecundaria2.class);

    private final CasoUsoRegistrarRespuestaSecundaria2 registro;
    private final String pod;
    private final ObjectMapper mapper = new ObjectMapper();

    public ConsumidorRespuestaSecundaria2(CasoUsoRegistrarRespuestaSecundaria2 registro,
            @Value("${ordermanager.pod:local}") String pod) {
        this.registro = registro;
        this.pod = pod;
    }

    @KafkaListener(topics = "${sagas.topics.respuesta-secundaria2:respuesta.secundaria2}")
    public void onRespuesta(String mensaje,
                            @Header(name = "kafka_receivedMessageKey", required = false) String key) throws Exception {
        JsonNode n = mapper.readTree(mensaje);
        var sagaId = OrdenId.de(n.get("sagaId").asText());   // clave de correlación puesta por PuertoSagaSecundaria2
        var mensajeId = n.get("mensajeId").asText();        // id único del evento (dedup)
        var exito = n.get("exito").asBoolean();

        if (exito) {
            registro.respuestaOk(sagaId, new RefRespuesta(n.get("ref").asText()), mensajeId);
        } else {
            registro.respuestaError(sagaId, n.get("codigo").asText(), n.get("detalle").asText(),
                    n.path("reintentable").asBoolean(true), mensajeId);
        }
        log.info("evento=respuesta_secundaria2_registrada orden={} tipo={} exito={} mensaje_id={} pod={}",
                sagaId.valor(), SagaSecundaria2.TIPO.valor(), exito, mensajeId, pod);
    }
}
