package com.ejemplo.app.infraestructure.ordermanager.saga;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;

/** Fila de la tabla hija {@code saga_auditoria}: una intervención de soporte. */
@Embeddable
class AuditoriaEntity {

    private Instant cuando;
    private String quien;
    private String accion;

    @Column(length = 500)
    private String detalle;

    protected AuditoriaEntity() {
        // requerido por JPA
    }

    AuditoriaEntity(Instant cuando, String quien, String accion, String detalle) {
        this.cuando = cuando;
        this.quien = quien;
        this.accion = accion;
        this.detalle = detalle;
    }

    Instant getCuando() { return cuando; }
    String getQuien() { return quien; }
    String getAccion() { return accion; }
    String getDetalle() { return detalle; }
}
