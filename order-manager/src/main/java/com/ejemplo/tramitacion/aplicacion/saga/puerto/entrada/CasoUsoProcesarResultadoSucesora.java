package com.ejemplo.tramitacion.aplicacion.saga.puerto.entrada;

import com.ejemplo.tramitacion.dominio.saga.general.MensajeId;
import com.ejemplo.tramitacion.dominio.saga.general.MotivoFallo;
import com.ejemplo.tramitacion.dominio.saga.general.Paso;
import com.ejemplo.tramitacion.dominio.saga.general.ResultadoPaso;
import com.ejemplo.tramitacion.dominio.saga.general.SagaId;

/**
 * Punto de convergencia de resultados de las sagas sucesoras.
 * Lo invocan: el consumer de Kafka del topic de respuesta del paso ASINCRONO
 * (con MensajeId externo para deduplicar) y el propio servicio tras
 * las llamadas síncronas de las sagas SECUENCIAL y SIMPLE.
 */
public interface CasoUsoProcesarResultadoSucesora {

    void pasoCompletado(SagaId id, Paso paso, ResultadoPaso resultado, MensajeId msgId);

    void pasoFallido(SagaId id, Paso paso, MotivoFallo motivo, MensajeId msgId);
}
