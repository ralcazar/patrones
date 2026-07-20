package com.ejemplo.app.infraestructure.sagas.sagasecundaria1.persistencia;

import java.util.UUID;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * Tabla satélite 1:1 con {@code orden} (PK = {@code orden_id}, FK a
 * {@code orden.orden_id}, SIN {@code ON DELETE CASCADE}): el contexto propio
 * de la saga secundaria 1.
 */
@Entity
@Table(name = "proceso_saga_secundaria1")
public class ProcesoSagaSecundaria1Entity {

    @Id
    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(name = "orden_id", length = 36)
    private UUID ordenId;

    @Column(name = "ref_paso1", nullable = false, length = 100)
    private String refPaso1;

    @Column(name = "ref_inicio", length = 100)
    private String refInicio;

    @Column(name = "ref_confirmacion", length = 100)
    private String refConfirmacion;

    protected ProcesoSagaSecundaria1Entity() {
        // requerido por JPA
    }

    public ProcesoSagaSecundaria1Entity(UUID ordenId, String refPaso1, String refInicio, String refConfirmacion) {
        this.ordenId = ordenId;
        this.refPaso1 = refPaso1;
        this.refInicio = refInicio;
        this.refConfirmacion = refConfirmacion;
    }

    public UUID getOrdenId() { return ordenId; }
    public String getRefPaso1() { return refPaso1; }
    public String getRefInicio() { return refInicio; }
    public String getRefConfirmacion() { return refConfirmacion; }
}
