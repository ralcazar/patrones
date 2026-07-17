package com.ejemplo.app.business.ordermanager.dominio;

import org.jmolecules.ddd.annotation.ValueObject;

import java.time.Instant;

@ValueObject
public record AuditoriaIntervencion(Instant cuando, UsuarioSoporte quien, String accion, String detalle) {
    public static AuditoriaIntervencion de(UsuarioSoporte quien, String accion, String detalle) {
        return new AuditoriaIntervencion(Instant.now(), quien, accion, detalle);
    }
}
