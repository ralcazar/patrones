package com.ejemplo.app.infraestructure.ordermanager.persistencia;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import org.springframework.data.domain.Persistable;

import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OrderColumn;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.Transient;
import jakarta.persistence.Version;

/**
 * Entidad JPA de OrdenRoot: la ÚNICA {@code @Version} del agregado, en UNA
 * ÚNICA fila que lleva TANTO el estado de negocio (FSM: {@code tipo},
 * {@code externalId}, {@code estado}, auditoría) COMO el de ejecución
 * (reintentos, lease del token, ticket, finalización). Antes de la fusión de
 * {@code orden}+{@code proceso} en una sola tabla, este agregado se leía en 2
 * SELECT separados con la {@code version} solo en uno de ellos, lo que podía
 * producir una lectura mixta (torn read) bajo READ_COMMITTED; con una única
 * fila y una única foto atómica ({@code findById}) eso deja de ser posible.
 *
 * {@code creadaEn}/{@code actualizadaEn} son bookkeeping puro de infraestructura
 * (para el criterio de limpieza y el modelo de lectura de soporte): el dominio
 * no los conoce, OrdenRoot no los modela.
 *
 * Implementa {@link Persistable} porque el {@code @Id} lo asigna el dominio
 * (UUID de cliente, no {@code @GeneratedValue}): con un {@code version}
 * primitivo, Spring Data no puede usarlo para decidir si la fila es nueva (su
 * detección por versión se desactiva para tipos primitivos) y cae al
 * criterio por defecto (id no nulo = "no nueva"), lo que llevaría a
 * {@code EntityManager.merge()} en vez de {@code persist()} incluso para
 * altas. Con la colección {@code auditoria} ya no vacía desde la fusión con
 * Proceso, ese {@code merge()} de una fila nueva dispara el INSERT + un
 * UPDATE extra de Hibernate al finalizar el manejo de la colección — y ese
 * UPDATE incrementa {@code version} de 0 a 1 en el alta, antes de que nadie
 * haya modificado nada. {@link #marcarComoNueva()} lo evita: {@code crear()}
 * la llama para forzar {@code persist()} (un único INSERT, version se queda
 * en 0); {@code guardar()} no la llama, así que sigue yendo por
 * {@code merge()} con el candado optimista real de JPA.
 */
@Entity
@Table(name = "orden")
public class OrdenEntity implements Persistable<UUID> {

    @Transient
    private boolean nueva;

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

    @Column(nullable = false)
    private int intentos;

    @Column(name = "proximo_reintento_en", nullable = false)
    private Instant proximoReintentoEn;

    @Column(name = "token_trabajador", length = 36)
    private String tokenTrabajador;

    @Column(name = "token_expira_en")
    private Instant tokenExpiraEn;

    @Column(name = "ticket_abierto_en")
    private Instant ticketAbiertoEn;

    @Column(name = "completada_en")
    private Instant completadaEn;

    @Column(name = "ultimo_error_tipo", length = 200)
    private String ultimoErrorTipo;

    @Column(name = "ultimo_error_mensaje", length = 1000)
    private String ultimoErrorMensaje;

    @Version
    @Column(nullable = false)
    private long version;

    @Column(name = "creada_en", nullable = false, updatable = false)
    private Instant creadaEn;

    @Column(name = "actualizada_en", nullable = false)
    private Instant actualizadaEn;

    protected OrdenEntity() {
        // requerido por JPA
    }

    public OrdenEntity(UUID ordenId, String tipo, String externalId, String estado, List<AuditoriaEntity> auditoria,
            int intentos, Instant proximoReintentoEn, String tokenTrabajador, Instant tokenExpiraEn,
            Instant ticketAbiertoEn, Instant completadaEn, String ultimoErrorTipo, String ultimoErrorMensaje,
            long version) {
        this.ordenId = ordenId;
        this.tipo = tipo;
        this.externalId = externalId;
        this.estado = estado;
        this.auditoria = auditoria;
        this.intentos = intentos;
        this.proximoReintentoEn = proximoReintentoEn;
        this.tokenTrabajador = tokenTrabajador;
        this.tokenExpiraEn = tokenExpiraEn;
        this.ticketAbiertoEn = ticketAbiertoEn;
        this.completadaEn = completadaEn;
        this.ultimoErrorTipo = ultimoErrorTipo;
        this.ultimoErrorMensaje = ultimoErrorMensaje;
        this.version = version;
    }

    @PrePersist
    void alCrear() {
        var ahora = Instant.now();
        if (creadaEn == null) {
            creadaEn = ahora;
        }
        actualizadaEn = ahora;
    }

    @PreUpdate
    void alActualizar() {
        actualizadaEn = Instant.now();
    }

    /** Solo la llama {@code AdaptadorRepositorioOrden.crear}: fuerza persist() en vez de merge(). */
    void marcarComoNueva() {
        this.nueva = true;
    }

    @Override
    public UUID getId() { return ordenId; }

    @Override
    public boolean isNew() { return nueva; }

    public UUID getOrdenId() { return ordenId; }
    public String getTipo() { return tipo; }
    public String getExternalId() { return externalId; }
    public String getEstado() { return estado; }
    public List<AuditoriaEntity> getAuditoria() { return auditoria; }
    public int getIntentos() { return intentos; }
    public Instant getProximoReintentoEn() { return proximoReintentoEn; }
    public String getTokenTrabajador() { return tokenTrabajador; }
    public Instant getTokenExpiraEn() { return tokenExpiraEn; }
    public Instant getTicketAbiertoEn() { return ticketAbiertoEn; }
    public Instant getCompletadaEn() { return completadaEn; }
    public String getUltimoErrorTipo() { return ultimoErrorTipo; }
    public String getUltimoErrorMensaje() { return ultimoErrorMensaje; }
    public long getVersion() { return version; }
    public Instant getCreadaEn() { return creadaEn; }
    public Instant getActualizadaEn() { return actualizadaEn; }
}
