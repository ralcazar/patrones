package com.ejemplo.app.infraestructure.sagas.sagasecundaria2.eventos;

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
 * de Kafka en la aplicación): el evento real solo trae éxito (no hay caso de
 * error en el contrato), así que este adaptador se limita a parsear el
 * evento y aplicarlo directamente sobre el agregado a través del caso de uso
 * de registro, en la misma transacción. Como adaptador de entrada, no toca
 * el agregado ni sus puertos de salida directamente: pasa por la capa de
 * aplicación (regla de arquitectura del CLAUDE.md). Ajusta el parseo del
 * evento al esquema real de tu topic.
 *
 * <p>Si Kafka reentrega el mensaje (entrega at-least-once), la segunda
 * aplicación es inofensiva porque {@code respuestaRecibida} lleva la saga a
 * un estado absorbente (TERMINADA): ver la caveat de diseño en el javadoc de
 * {@code ServicioRegistrarRespuestaSecundaria2}.
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
        var mensajeId = n.get("mensajeId").asText();        // id único del evento, solo para el log

        registro.respuestaOk(sagaId, new RefRespuesta(n.get("ref").asText()));

        log.info("evento=respuesta_secundaria2_registrada orden={} tipo={} exito={} mensaje_id={} pod={}",
                sagaId.valor(), SagaSecundaria2.TIPO.valor(), true, mensajeId, pod);
    }
}
