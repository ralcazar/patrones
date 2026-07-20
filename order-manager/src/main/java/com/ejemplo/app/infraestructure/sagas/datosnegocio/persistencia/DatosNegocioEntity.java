package com.ejemplo.app.infraestructure.sagas.datosnegocio.persistencia;

import java.time.LocalDate;
import java.util.UUID;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * Entidad JPA de {@link com.ejemplo.app.business.sagas.dominio.datosnegocio.DatosNegocio}:
 * SOLO escalares, sin {@code @OneToMany} a los documentos (evita cargas
 * accidentales de blobs; los documentos se consultan aparte, ver
 * {@link DocumentoNegocioJpaRepository}).
 */
@Entity
@Table(name = "datos_negocio")
public class DatosNegocioEntity {

    @Id
    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(name = "datosnegocio_id", length = 36)
    private UUID datosnegocioId;

    // unique = true: replica en el DDL que genera Hibernate para H2 (los tests de
    // integración, ddl-auto=create-drop) el índice único real de Oracle
    // (idx_datos_negocio_external_id en db/datos_negocio.sql, aplicado a mano).
    @Column(name = "external_id", nullable = false, length = 36, unique = true)
    private String externalId;

    @Column(name = "dato_negocio1", nullable = false)
    private Integer datoNegocio1;

    @Column(name = "dato_negocio2", nullable = false)
    private LocalDate datoNegocio2;

    @Column(name = "dato_negocio3", nullable = false, length = 400)
    private String datoNegocio3;

    protected DatosNegocioEntity() {
        // requerido por JPA
    }

    public DatosNegocioEntity(UUID datosnegocioId, String externalId, Integer datoNegocio1,
            LocalDate datoNegocio2, String datoNegocio3) {
        this.datosnegocioId = datosnegocioId;
        this.externalId = externalId;
        this.datoNegocio1 = datoNegocio1;
        this.datoNegocio2 = datoNegocio2;
        this.datoNegocio3 = datoNegocio3;
    }

    public UUID getDatosnegocioId() { return datosnegocioId; }
    public String getExternalId() { return externalId; }
    public Integer getDatoNegocio1() { return datoNegocio1; }
    public LocalDate getDatoNegocio2() { return datoNegocio2; }
    public String getDatoNegocio3() { return datoNegocio3; }
}
