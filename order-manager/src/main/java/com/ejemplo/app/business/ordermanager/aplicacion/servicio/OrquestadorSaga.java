package com.ejemplo.app.business.ordermanager.aplicacion.servicio;

import com.ejemplo.app.business.ordermanager.dominio.comun.OrdenRoot;
import com.ejemplo.app.business.ordermanager.dominio.comun.TipoSaga;

/**
 * Ejecuta UN paso de una saga concreta: recibe el agregado ya cargado por el
 * llamante (una única carga por paso, antes del REST; ver
 * {@link ServicioContinuarSaga}), hace el REST del paso fuera de transacción
 * (puede lanzar; el llamante lo convierte en reintento sobre esa misma
 * instancia) y, en UNA sola transacción, aplica el resultado a la saga y la
 * parte operativa de la señal devuelta (reset de intentos + renovación de
 * lease, o aparcar, o finalizar), y guarda esa misma instancia una única vez.
 * Una implementación por TipoSaga.
 */
public interface OrquestadorSaga {

    TipoSaga tipo();

    SenalPaso ejecutarPaso(OrdenRoot orden);
}
