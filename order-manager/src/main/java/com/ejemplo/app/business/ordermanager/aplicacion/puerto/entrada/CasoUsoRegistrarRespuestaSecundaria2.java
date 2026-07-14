package com.ejemplo.app.business.ordermanager.aplicacion.puerto.entrada;

import com.ejemplo.app.business.ordermanager.dominio.comun.SagaId;
import com.ejemplo.app.business.ordermanager.dominio.sagasecundaria2.RefRespuesta;

/**
 * Entrada del consumer de Kafka de la saga secundaria 2: registra la respuesta
 * diferida como tarea durable (intake fino: encolar y ack). El adaptador de
 * entrada no toca la cola directamente — pasa por este caso de uso, que es
 * quien conoce el modelo de tareas.
 */
public interface CasoUsoRegistrarRespuestaSecundaria2 {

    void respuestaOk(SagaId sagaId, RefRespuesta ref, String mensajeId);

    void respuestaError(SagaId sagaId, String codigo, String detalle,
                        boolean reintentable, String mensajeId);
}
