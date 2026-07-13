package com.ejemplo.tramitacion.dominio.saga.general;

import java.time.Instant;

public record AuditoriaIntervencion(Instant cuando, UsuarioSoporte quien, String accion, String detalle) {
    public static AuditoriaIntervencion de(UsuarioSoporte quien, String accion, String detalle) {
        return new AuditoriaIntervencion(Instant.now(), quien, accion, detalle);
    }
}
