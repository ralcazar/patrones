package com.ejemplo.app.infraestructure.ordermanager.cola;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

/**
 * Cada ítem a procesar por el GestorOrdenes. FUSIÓN CON LAS SAGAS: una orden
 * es "continuar una saga". El contenido es una TareaSaga serializada (ver
 * CodecTareaSaga); ejecutarDesde permite tareas diferidas (reintentos con
 * backoff, timeout del paso asíncrono); sagaId y tipoTarea son metadatos
 * opacos para consultas y trazas: el gestor NO conoce el modelo de sagas.
 *
 * El ciclo de vida del estado lo mueve el reclamo/finalización atómico vía SQL
 * en RepositorioOrdenes (patrón lease).
 *
 * Nota pragmática: la entidad lleva anotaciones JPA aunque viva en el dominio
 * del gestor; el patrón lease se apoya directamente en la tabla y separar una
 * entidad de persistencia solo añadiría un mapeo 1:1.
 */
@Entity
@Table(name = "ordenes")
public class Orden {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private EstadoOrden estado = EstadoOrden.PENDIENTE;

    @Column(name = "reclamada_en")
    private Instant reclamadaEn;

    @Column(length = 64)
    private String token;

    /** TareaSaga serializada (JSON con campo discriminador "tipo"). */
    @Column(columnDefinition = "text")
    private String contenido;

    /** La orden no es elegible hasta este instante. Base del backoff y los timeouts. */
    @Column(name = "ejecutar_desde", nullable = false)
    private Instant ejecutarDesde = Instant.now();

    /** Saga a la que pertenece la tarea (trazas y pantalla de soporte). */
    @Column(name = "saga_id", length = 36)
    private String sagaId;

    /** Discriminador de la tarea (INICIAR, ARRANCAR_SAGA, REINTENTAR...). */
    @Column(name = "tipo_tarea", length = 30)
    private String tipoTarea;

    @Column(name = "creada_en", nullable = false, updatable = false)
    private Instant creadaEn = Instant.now();

    protected Orden() {
        // requerido por JPA
    }

    /** Crea una orden-tarea de saga, opcionalmente diferida. */
    public static Orden tareaSaga(String contenido, String sagaId, String tipoTarea, Instant ejecutarDesde) {
        Orden o = new Orden();
        o.estado = EstadoOrden.PENDIENTE;
        o.contenido = contenido;
        o.sagaId = sagaId;
        o.tipoTarea = tipoTarea;
        o.ejecutarDesde = ejecutarDesde != null ? ejecutarDesde : Instant.now();
        o.creadaEn = Instant.now();
        return o;
    }

    public Long getId() { return id; }
    public EstadoOrden getEstado() { return estado; }
    public Instant getReclamadaEn() { return reclamadaEn; }
    public String getToken() { return token; }
    public String getContenido() { return contenido; }
    public Instant getEjecutarDesde() { return ejecutarDesde; }
    public String getSagaId() { return sagaId; }
    public String getTipoTarea() { return tipoTarea; }
    public Instant getCreadaEn() { return creadaEn; }
}
