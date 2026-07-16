package com.ejemplo.app.business.ordermanager.aplicacion.servicio;

import com.ejemplo.app.business.ordermanager.dominio.comun.SagaId;
import com.ejemplo.app.business.ordermanager.dominio.comun.TipoSaga;

/**
 * Ejecuta UN paso de una saga concreta: hace el REST del paso fuera de
 * transacción (puede lanzar; el llamante lo convierte en reintento) y, en
 * UNA sola transacción, recarga el agregado, aplica el resultado a la saga y
 * la parte operativa de la señal devuelta (reset de intentos + renovación de
 * lease, o aparcar, o finalizar), y guarda una única vez. Una implementación
 * por TipoSaga.
 */
public interface OrquestadorSaga {

    TipoSaga tipo();

    SenalPaso ejecutarPaso(SagaId id);
}
