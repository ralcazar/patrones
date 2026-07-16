package com.ejemplo.app.infraestructure.ordermanager.saga;

import java.util.ArrayList;
import java.util.List;

import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.Lob;
import jakarta.persistence.OrderColumn;
import jakarta.persistence.Table;

/**
 * Entidad JPA de la SagaRoot: SIN {@code @Version} (la controla OrdenEntity,
 * la única del agregado). Los campos propios de cada tipo de saga (las refs y
 * datos de negocio) son, en el dominio, siempre wrappers de un único String,
 * así que se guardan como un JSON plano en {@code contexto} en vez de mapear
 * columna a columna cuatro formas distintas: el adaptador despacha por
 * {@code tipo} al (de)serializarlo.
 */
@Entity
@Table(name = "saga")
public class SagaEntity {

    @Id
    @Column(name = "saga_id", length = 36)
    private String sagaId;

    @Column(nullable = false, length = 20)
    private String tipo;

    @Column(name = "external_id", nullable = false, length = 36)
    private String externalId;

    @Column(nullable = false, length = 40)
    private String estado;

    @Lob
    @Column(nullable = false)
    private String contexto;

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "saga_auditoria", joinColumns = @JoinColumn(name = "saga_id"))
    @OrderColumn(name = "orden_secuencia")
    private List<AuditoriaEntity> auditoria = new ArrayList<>();

    protected SagaEntity() {
        // requerido por JPA
    }

    public SagaEntity(String sagaId, String tipo, String externalId, String estado, String contexto,
            List<AuditoriaEntity> auditoria) {
        this.sagaId = sagaId;
        this.tipo = tipo;
        this.externalId = externalId;
        this.estado = estado;
        this.contexto = contexto;
        this.auditoria = auditoria;
    }

    public String getSagaId() { return sagaId; }
    public String getTipo() { return tipo; }
    public String getExternalId() { return externalId; }
    public String getEstado() { return estado; }
    public String getContexto() { return contexto; }
    public List<AuditoriaEntity> getAuditoria() { return auditoria; }
}
