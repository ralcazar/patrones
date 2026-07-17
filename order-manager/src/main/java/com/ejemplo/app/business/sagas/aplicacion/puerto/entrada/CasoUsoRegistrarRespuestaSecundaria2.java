package com.ejemplo.app.business.sagas.aplicacion.puerto.entrada;

import com.ejemplo.app.business.ordermanager.dominio.comun.SagaId;
import com.ejemplo.app.business.sagas.dominio.sagasecundaria2.RefRespuesta;

/**
 * Entrada del consumer de Kafka de la saga secundaria 2: aplica la respuesta
 * diferida directamente sobre el agregado (una transacción, deduplicada por
 * mensajeId). El adaptador de entrada no toca el agregado ni sus puertos de
 * salida directamente — pasa por este caso de uso, que es quien conoce cómo
 * mutarlo.
 */
public interface CasoUsoRegistrarRespuestaSecundaria2 {

    void respuestaOk(SagaId sagaId, RefRespuesta ref, String mensajeId);

    void respuestaError(SagaId sagaId, String codigo, String detalle,
                        boolean reintentable, String mensajeId);
}
