package com.ejemplo.app.infraestructure.ordermanager.persistencia;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

/**
 * Entidad JPA de OrdenRoot: la ÚNICA {@code @Version} del agregado (negocio +
 * ejecución en una sola fila). {@code ordenId} es también FK a {@code proceso}.
 *
 * {@code creadaEn}/{@code actualizadaEn} son bookkeeping puro de infraestructura
 * (para el criterio de limpieza y el modelo de lectura de soporte): el dominio
 * no los conoce, OrdenRoot no los modela.
 */
@Entity
@Table(name = "orden")
public class OrdenEntity {

    @Id
    @Column(name = "orden_id", length = 36)
    private String ordenId;

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

    @Column(length = 30)
    private String resultado;

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

    public OrdenEntity(String ordenId, int intentos, Instant proximoReintentoEn, String tokenTrabajador,
            Instant tokenExpiraEn, Instant ticketAbiertoEn, String resultado, long version) {
        this.ordenId = ordenId;
        this.intentos = intentos;
        this.proximoReintentoEn = proximoReintentoEn;
        this.tokenTrabajador = tokenTrabajador;
        this.tokenExpiraEn = tokenExpiraEn;
        this.ticketAbiertoEn = ticketAbiertoEn;
        this.resultado = resultado;
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

    public String getOrdenId() { return ordenId; }
    public int getIntentos() { return intentos; }
    public Instant getProximoReintentoEn() { return proximoReintentoEn; }
    public String getTokenTrabajador() { return tokenTrabajador; }
    public Instant getTokenExpiraEn() { return tokenExpiraEn; }
    public Instant getTicketAbiertoEn() { return ticketAbiertoEn; }
    public String getResultado() { return resultado; }
    public long getVersion() { return version; }
    public Instant getCreadaEn() { return creadaEn; }
    public Instant getActualizadaEn() { return actualizadaEn; }
}
