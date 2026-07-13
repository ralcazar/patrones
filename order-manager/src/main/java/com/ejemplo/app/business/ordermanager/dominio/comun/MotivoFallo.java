package com.ejemplo.app.business.ordermanager.dominio.comun;

import org.jmolecules.ddd.annotation.ValueObject;

@ValueObject
public record MotivoFallo(String codigo, String detalle, boolean esReintentable) {
    public static MotivoFallo timeout() {
        return new MotivoFallo("TIMEOUT", "Sin respuesta del servicio en el plazo esperado", true);
    }
    public static MotivoFallo errorTecnico(String detalle) {
        return new MotivoFallo("ERROR_TECNICO", detalle, true);
    }
    /** Error funcional (p. ej. 4xx de negocio): reintentar no lo arreglará. */
    public static MotivoFallo errorNegocio(String detalle) {
        return new MotivoFallo("ERROR_NEGOCIO", detalle, false);
    }
}
