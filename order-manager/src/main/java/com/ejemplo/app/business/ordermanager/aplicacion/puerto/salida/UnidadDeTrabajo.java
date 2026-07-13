package com.ejemplo.app.business.ordermanager.aplicacion.puerto.salida;

import java.util.function.Supplier;

/**
 * Delimita la transacción. El adaptador la implementa (p. ej. @Transactional).
 * Regla clave del diseño: guardar la principal como COMPLETADA y crear las
 * 3 sagas que le siguen ocurre dentro de UNA MISMA invocación a enTransaccion.
 */
public interface UnidadDeTrabajo {

    <T> T enTransaccion(Supplier<T> accion);

    default void enTransaccion(Runnable accion) {
        enTransaccion(() -> {
            accion.run();
            return null;
        });
    }
}
