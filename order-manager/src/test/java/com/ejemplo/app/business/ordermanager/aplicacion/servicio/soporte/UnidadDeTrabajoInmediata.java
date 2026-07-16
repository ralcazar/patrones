package com.ejemplo.app.business.ordermanager.aplicacion.servicio.soporte;

import java.util.function.Supplier;

import com.ejemplo.app.business.ordermanager.aplicacion.puerto.salida.UnidadDeTrabajo;

/** Fake de UnidadDeTrabajo para tests: ejecuta la acción tal cual, sin demarcar transacción real. */
public final class UnidadDeTrabajoInmediata implements UnidadDeTrabajo {

    @Override
    public <T> T enTransaccion(Supplier<T> accion) {
        return accion.get();
    }
}
