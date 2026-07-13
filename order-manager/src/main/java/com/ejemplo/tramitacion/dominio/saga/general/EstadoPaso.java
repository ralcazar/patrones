package com.ejemplo.tramitacion.dominio.saga.general;

public enum EstadoPaso {
    PENDIENTE,
    SOLICITADO,
    ESPERANDO_REINTENTO,
    BLOQUEADO_SOPORTE,
    COMPLETADO,
    COMPLETADO_MANUAL,
    CANCELADO,
    COMPENSADO;

    /** El paso está "vivo": puede seguir evolucionando. */
    public boolean esActivo() {
        return this == SOLICITADO || this == ESPERANDO_REINTENTO || this == BLOQUEADO_SOPORTE;
    }

    public boolean cuentaComoCompletado() {
        return this == COMPLETADO || this == COMPLETADO_MANUAL;
    }
}
