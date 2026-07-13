package com.ejemplo.tramitacion.aplicacion.saga.puerto.entrada;

import com.ejemplo.tramitacion.dominio.saga.general.MensajeId;
import com.ejemplo.tramitacion.dominio.saga.general.MotivoFallo;
import com.ejemplo.tramitacion.dominio.saga.general.Paso;
import com.ejemplo.tramitacion.dominio.saga.general.ResultadoPaso;
import com.ejemplo.tramitacion.dominio.saga.general.SagaId;

/**
 * Punto de convergencia de resultados de la saga principal.
 * Todos sus pasos son síncronos, así que lo invoca el propio servicio
 * de aplicación tras cada llamada REST; queda como puerto por si algún
 * paso pasara a ser asíncrono en el futuro.
 */
public interface CasoUsoProcesarResultadoPrincipal {

    void pasoCompletado(SagaId id, Paso paso, ResultadoPaso resultado, MensajeId msgId);

    void pasoFallido(SagaId id, Paso paso, MotivoFallo motivo, MensajeId msgId);
}
