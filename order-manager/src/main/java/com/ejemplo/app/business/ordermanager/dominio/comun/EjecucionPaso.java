package com.ejemplo.app.business.ordermanager.dominio.comun;

import org.jmolecules.ddd.annotation.ValueObject;

/**
 * Estado de ejecución de un paso. Value object inmutable: las transiciones no
 * mutan en sitio, devuelven una nueva instancia. Las gobiernan los agregados
 * (métodos package-private, vía {@code Saga.transformar}); el exterior solo lee.
 */
@ValueObject
public record EjecucionPaso<P extends Enum<P> & PasoSaga>(
        P paso, EstadoPaso estado, int intentos, MotivoFallo ultimoFallo) {

    public static <P extends Enum<P> & PasoSaga> EjecucionPaso<P> nuevo(P paso) {
        return new EjecucionPaso<>(paso, EstadoPaso.PENDIENTE, 0, null);
    }

    /** Para el adaptador de persistencia. */
    public static <P extends Enum<P> & PasoSaga> EjecucionPaso<P> rehidratar(
            P paso, EstadoPaso estado, int intentos, MotivoFallo ultimoFallo) {
        return new EjecucionPaso<>(paso, estado, intentos, ultimoFallo);
    }

    EjecucionPaso<P> solicitar()        { return conEstado(EstadoPaso.SOLICITADO); }
    EjecucionPaso<P> completar()        { return conEstado(EstadoPaso.COMPLETADO); }
    EjecucionPaso<P> completarManual()  { return conEstado(EstadoPaso.COMPLETADO_MANUAL); }
    EjecucionPaso<P> esperarReintento() { return conEstado(EstadoPaso.ESPERANDO_REINTENTO); }
    EjecucionPaso<P> bloquear()         { return conEstado(EstadoPaso.BLOQUEADO_SOPORTE); }
    EjecucionPaso<P> cancelar()         { return conEstado(EstadoPaso.CANCELADO); }
    EjecucionPaso<P> compensado()       { return conEstado(EstadoPaso.COMPENSADO); }

    EjecucionPaso<P> resetearIntentos() { return new EjecucionPaso<>(paso, estado, 0, null); }

    EjecucionPaso<P> registrarFallo(MotivoFallo motivo) {
        return new EjecucionPaso<>(paso, estado, intentos + 1, motivo);
    }

    private EjecucionPaso<P> conEstado(EstadoPaso nuevoEstado) {
        return new EjecucionPaso<>(paso, nuevoEstado, intentos, ultimoFallo);
    }
}
