package com.ejemplo.app.business.sagas.aplicacion.puerto.salida;

import com.ejemplo.app.business.ordermanager.dominio.ExternalId;
import com.ejemplo.app.business.ordermanager.dominio.MotivoFallo;
import com.ejemplo.app.business.ordermanager.dominio.OrdenId;
import com.ejemplo.app.business.sagas.dominio.sagasecundaria2.RefRespuesta;

/**
 * Servicio REST de conciliación de la saga secundaria 2: cuando el timeout de
 * 24h vence sin respuesta Kafka, permite preguntar al servicio destino si el
 * resultado ya existe (el evento pudo perderse o estar aún en camino).
 * El adaptador falla con ExcepcionServicioExterno si la consulta no responde.
 */
public interface PuertoConciliacionSecundaria2 {

    Resultado consultar(OrdenId sagaId, ExternalId externalId);

    /** Lo que la conciliación puede contar del estado de la solicitud. */
    sealed interface Resultado {

        /** El servicio destino terminó bien: mismo contenido que el evento Kafka Ok. */
        record Disponible(RefRespuesta ref) implements Resultado {}

        /** El servicio destino terminó con error: mismo contenido que el evento Kafka Error. */
        record FalloRegistrado(MotivoFallo motivo) implements Resultado {}

        /** El servicio destino aún no tiene resultado: seguir esperando. */
        record SinResultado() implements Resultado {}
    }
}
