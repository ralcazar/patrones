package com.ejemplo.app.business.ordermanager.aplicacion.puerto.entrada;

import com.ejemplo.app.business.ordermanager.dominio.comun.MensajeId;
import com.ejemplo.app.business.ordermanager.dominio.comun.MotivoFallo;
import com.ejemplo.app.business.ordermanager.dominio.comun.PasoSaga;
import com.ejemplo.app.business.ordermanager.dominio.comun.ResultadoPaso;
import com.ejemplo.app.business.ordermanager.dominio.comun.SagaId;

/**
 * Punto de convergencia de resultados de paso, genérico en el enum de pasos
 * de cada saga. Lo invocan: el propio servicio de aplicación tras cada llamada
 * REST síncrona, y (solo para la saga secundaria 2) el consumer de Kafka del
 * topic de respuesta, con MensajeId externo para deduplicar.
 */
public interface CasoUsoProcesarResultadoPaso<P extends Enum<P> & PasoSaga> {

    void pasoCompletado(SagaId id, P paso, ResultadoPaso resultado, MensajeId msgId);

    void pasoFallido(SagaId id, P paso, MotivoFallo motivo, MensajeId msgId);
}
