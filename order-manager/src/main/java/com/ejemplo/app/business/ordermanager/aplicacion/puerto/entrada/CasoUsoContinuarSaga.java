package com.ejemplo.app.business.ordermanager.aplicacion.puerto.entrada;

import com.ejemplo.app.business.ordermanager.dominio.comun.SagaId;
import com.ejemplo.app.business.ordermanager.dominio.comun.TipoSaga;

/**
 * Continúa (o arranca) la ejecución de una saga: reclama el token de
 * ejecución, avanza pasos síncronos hasta aparcar o terminar, y persiste en
 * cada paso. Lo invocan los workers pull (una candidata por pull) y
 * cualquier evento que despierte una orden (respuesta Kafka, intervención de
 * soporte).
 */
public interface CasoUsoContinuarSaga {

    void continuar(SagaId id, TipoSaga tipo);

    /**
     * Reclama y ejecuta la siguiente candidata elegible (modelo pull: lo invoca
     * cada worker al quedarse libre). Devuelve false si no queda trabajo.
     */
    boolean continuarSiguiente();

    /** ¿Hay alguna candidata elegible ahora mismo? (para que el planificador despierte workers). */
    boolean hayTrabajoPendiente();
}
