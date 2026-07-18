package com.ejemplo.app.infraestructure.ordermanager.persistencia;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OrderColumn;
import jakarta.persistence.Table;

/**
 * Entidad JPA del Proceso: SIN {@code @Version} (la controla OrdenEntity,
 * la única del agregado). Los campos propios de cada tipo de orden (las refs y
 * datos de negocio) viven en una tabla satélite relacional por tipo (ver
 * {@link MapeadorProceso#guardarContexto}/{@link MapeadorProceso#rearmar}),
 * no aquí: esta entidad solo lleva el estado de la FSM común a todos los tipos.
 */
@Entity
@Table(name = "proceso")
public class ProcesoEntity {

    @Id
    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(name = "orden_id", length = 36)
    private UUID ordenId;

    @Column(nullable = false, length = 20)
    private String tipo;

    @Column(name = "external_id", nullable = false, length = 36)
    private String externalId;

    @Column(nullable = false, length = 40)
    private String estado;

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "proceso_auditoria", joinColumns = @JoinColumn(name = "orden_id"))
    @OrderColumn(name = "secuencia")
    private List<AuditoriaEntity> auditoria = new ArrayList<>();

    protected ProcesoEntity() {
        // requerido por JPA
    }

    public ProcesoEntity(UUID ordenId, String tipo, String externalId, String estado,
            List<AuditoriaEntity> auditoria) {
        this.ordenId = ordenId;
        this.tipo = tipo;
        this.externalId = externalId;
        this.estado = estado;
        this.auditoria = auditoria;
    }

    public UUID getOrdenId() { return ordenId; }
    public String getTipo() { return tipo; }
    public String getExternalId() { return externalId; }
    public String getEstado() { return estado; }
    public List<AuditoriaEntity> getAuditoria() { return auditoria; }
}
