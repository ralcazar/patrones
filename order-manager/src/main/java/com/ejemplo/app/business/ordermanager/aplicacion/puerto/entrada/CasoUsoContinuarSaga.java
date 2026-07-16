package com.ejemplo.app.business.ordermanager.aplicacion.puerto.entrada;

import com.ejemplo.app.business.ordermanager.dominio.comun.SagaId;
import com.ejemplo.app.business.ordermanager.dominio.comun.TipoSaga;

/**
 * Continúa (o arranca) la ejecución de una saga: reclama el token de
 * ejecución, avanza pasos síncronos hasta aparcar o terminar, y persiste en
 * cada paso. Lo invoca el planificador (una vez por candidata elegible) y
 * cualquier evento que despierte una orden (respuesta Kafka, intervención de
 * soporte).
 */
public interface CasoUsoContinuarSaga {

    void continuar(SagaId id, TipoSaga tipo);

    /**
     * Barre las órdenes candidatas elegibles ahora mismo (hasta {@code limite})
     * y continúa cada una. Lo invoca el planificador: así el adaptador de
     * entrada nunca toca RepositorioOrden (puerto de salida) directamente.
     */
    void continuarCandidatas(int limite);
}
