package com.ejemplo.app.infraestructure.sagas.persistencia;

import java.util.UUID;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * Tabla satélite 1:1 con {@code proceso} (PK = {@code orden_id}, FK a
 * {@code proceso.orden_id}, SIN {@code ON DELETE CASCADE}): el contexto propio
 * de la saga secundaria 3 (antes en el CLOB {@code proceso.contexto}).
 */
@Entity
@Table(name = "proceso_saga_secundaria3")
public class ProcesoSagaSecundaria3Entity {

    @Id
    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(name = "orden_id", length = 36)
    private UUID ordenId;

    @Column(name = "ref_paso7", nullable = false, length = 100)
    private String refPaso7;

    @Column(name = "ref_ejecucion", length = 100)
    private String refEjecucion;

    protected ProcesoSagaSecundaria3Entity() {
        // requerido por JPA
    }

    public ProcesoSagaSecundaria3Entity(UUID ordenId, String refPaso7, String refEjecucion) {
        this.ordenId = ordenId;
        this.refPaso7 = refPaso7;
        this.refEjecucion = refEjecucion;
    }

    public UUID getOrdenId() { return ordenId; }
    public String getRefPaso7() { return refPaso7; }
    public String getRefEjecucion() { return refEjecucion; }
}
