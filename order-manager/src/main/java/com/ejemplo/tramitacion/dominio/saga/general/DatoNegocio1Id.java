package com.ejemplo.tramitacion.dominio.saga.general;

import java.util.UUID;

/** Identificador de negocio del DatoNegocio1 que se tramita. */
public record DatoNegocio1Id(UUID valor) {
    public static DatoNegocio1Id de(String valor) { return new DatoNegocio1Id(UUID.fromString(valor)); }
}
