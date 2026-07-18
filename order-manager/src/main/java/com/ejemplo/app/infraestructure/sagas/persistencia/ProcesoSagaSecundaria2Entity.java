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
 * de la saga secundaria 2 (antes en el CLOB {@code proceso.contexto}).
 */
@Entity
@Table(name = "proceso_saga_secundaria2")
public class ProcesoSagaSecundaria2Entity {

    @Id
    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(name = "orden_id", length = 36)
    private UUID ordenId;

    @Column(name = "ref_paso5", nullable = false, length = 100)
    private String refPaso5;

    @Column(name = "ref_respuesta", length = 100)
    private String refRespuesta;

    protected ProcesoSagaSecundaria2Entity() {
        // requerido por JPA
    }

    public ProcesoSagaSecundaria2Entity(UUID ordenId, String refPaso5, String refRespuesta) {
        this.ordenId = ordenId;
        this.refPaso5 = refPaso5;
        this.refRespuesta = refRespuesta;
    }

    public UUID getOrdenId() { return ordenId; }
    public String getRefPaso5() { return refPaso5; }
    public String getRefRespuesta() { return refRespuesta; }
}
