package com.ejemplo.app.infraestructure.ordermanager.programados;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

import com.ejemplo.app.business.ordermanager.aplicacion.puerto.entrada.CasoUsoContinuarOrden;

/**
 * Worker pull: encadena órdenes (al acabar una, va a la BD a por la siguiente)
 * hasta que no queda trabajo, y entonces muere. Lo despierta el planificador;
 * el tope de N concurrentes lo impone el pool "ejecutorContinuacion"
 * (maxPool = N, sin cola, descarte silencioso de los envíos sobrantes).
 */
@Component
public class TrabajadorContinuacion {

    private static final Logger log = LoggerFactory.getLogger(TrabajadorContinuacion.class);

    private final CasoUsoContinuarOrden casoUso;

    public TrabajadorContinuacion(CasoUsoContinuarOrden casoUso) {
        this.casoUso = casoUso;
    }

    @Async("ejecutorContinuacion")
    public void trabajar() {
        try {
            while (casoUso.continuarSiguiente()) {
            }
        } catch (RuntimeException e) {
            // Fallo de infraestructura (BD caída, etc.): el worker muere y el
            // siguiente tick del planificador volverá a levantar workers.
            log.error("Worker de continuación detenido por error", e);
        }
    }
}
