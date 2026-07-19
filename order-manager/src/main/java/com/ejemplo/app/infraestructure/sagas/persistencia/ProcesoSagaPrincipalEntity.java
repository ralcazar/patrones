package com.ejemplo.app.infraestructure.sagas.persistencia;

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
 * de la saga principal. Las refs aún no producidas son NULL.
 */
@Entity
@Table(name = "proceso_saga_principal")
public class ProcesoSagaPrincipalEntity {

    @Id
    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(name = "orden_id", length = 36)
    private UUID ordenId;

    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(name = "datosnegocio_id", nullable = false, length = 36)
    private UUID datosnegocioId;

    @Column(name = "ref_paso1", length = 100)
    private String refPaso1;

    @Column(name = "ref_paso2", length = 100)
    private String refPaso2;

    @Column(name = "ref_paso3", length = 100)
    private String refPaso3;

    @Column(name = "ref_paso4", length = 100)
    private String refPaso4;

    @Column(name = "ref_paso5", length = 100)
    private String refPaso5;

    @Column(name = "ref_paso6", length = 100)
    private String refPaso6;

    @Column(name = "ref_paso7", length = 100)
    private String refPaso7;

    @Column(name = "ref_paso8", length = 100)
    private String refPaso8;

    protected ProcesoSagaPrincipalEntity() {
        // requerido por JPA
    }

    public ProcesoSagaPrincipalEntity(UUID ordenId, UUID datosnegocioId, String refPaso1, String refPaso2,
            String refPaso3, String refPaso4, String refPaso5, String refPaso6, String refPaso7, String refPaso8) {
        this.ordenId = ordenId;
        this.datosnegocioId = datosnegocioId;
        this.refPaso1 = refPaso1;
        this.refPaso2 = refPaso2;
        this.refPaso3 = refPaso3;
        this.refPaso4 = refPaso4;
        this.refPaso5 = refPaso5;
        this.refPaso6 = refPaso6;
        this.refPaso7 = refPaso7;
        this.refPaso8 = refPaso8;
    }

    public UUID getOrdenId() { return ordenId; }
    public UUID getDatosnegocioId() { return datosnegocioId; }
    public String getRefPaso1() { return refPaso1; }
    public String getRefPaso2() { return refPaso2; }
    public String getRefPaso3() { return refPaso3; }
    public String getRefPaso4() { return refPaso4; }
    public String getRefPaso5() { return refPaso5; }
    public String getRefPaso6() { return refPaso6; }
    public String getRefPaso7() { return refPaso7; }
    public String getRefPaso8() { return refPaso8; }
}
