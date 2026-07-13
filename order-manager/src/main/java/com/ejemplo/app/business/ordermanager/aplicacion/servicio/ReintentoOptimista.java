package com.ejemplo.app.business.ordermanager.aplicacion.servicio;

import java.util.function.Supplier;

import com.ejemplo.app.business.ordermanager.dominio.comun.ConcurrenciaOptimistaException;

/**
 * Reintenta una acción transaccional ante conflicto de versión optimista.
 * Necesario porque varios hilos/consumers pueden mutar la misma saga a la vez.
 */
final class ReintentoOptimista {

    private static final int MAX_REINTENTOS = 5;

    private ReintentoOptimista() {}

    static <T> T ejecutar(Supplier<T> accion) {
        ConcurrenciaOptimistaException ultima = null;
        for (int i = 0; i < MAX_REINTENTOS; i++) {
            try {
                return accion.get();
            } catch (ConcurrenciaOptimistaException e) {
                ultima = e; // recargar y reaplicar: la acción vuelve a cargar la saga
            }
        }
        throw ultima;
    }
}
