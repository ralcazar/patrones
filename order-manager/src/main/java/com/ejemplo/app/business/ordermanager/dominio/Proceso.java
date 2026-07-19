package com.ejemplo.app.business.ordermanager.dominio;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import org.jmolecules.ddd.annotation.ValueObject;

/**
 * Ciclo de vida de NEGOCIO compartido por los tipos de orden: una FSM (el
 * estado E) más la auditoría de intervenciones de soporte. Value object
 * INMUTABLE: toda transición (paso aplicado, marcado manual, auditoría, y
 * las específicas de cada saga) devuelve una instancia NUEVA en vez de
 * mutar la existente. La raíz {@link OrdenRoot} es quien la contiene y
 * quien sustituye el valor tras cada transición: no conoce reintentos,
 * tiempo ni token de ejecución (eso es responsabilidad de OrdenRoot), ni
 * tiene su propia {@code version} (la controla el agregado).
 */
@ValueObject
public abstract class Proceso<E extends Enum<E>> {

    protected final OrdenId id;
    protected final ExternalId externalId;
    protected final E estado;
    protected final List<AuditoriaIntervencion> auditoria;

    /** Constructor de rehidratación para el adaptador de persistencia. */
    protected Proceso(OrdenId id, ExternalId externalId, E estado, List<AuditoriaIntervencion> auditoria) {
        this.id = id;
        this.externalId = externalId;
        this.estado = estado;
        this.auditoria = List.copyOf(auditoria);
    }

    public abstract TipoOrden tipo();

    /** Comando del paso en el que está ahora la FSM de negocio. */
    public abstract ComandoPaso comandoActual();

    /** Aplica el resultado del paso actual al contexto del proceso y devuelve la instancia siguiente, ya avanzada. */
    public abstract Proceso<E> aplicarYAvanzar(ResultadoPaso resultado);

    /** La FSM alcanzó un estado final: no queda ningún paso que ejecutar. */
    public abstract boolean terminada();

    /**
     * Soporte marca OK a mano el paso pendiente actual, aportando los datos
     * que ese paso habría producido, y devuelve la instancia siguiente.
     */
    public abstract Proceso<E> marcarPasoActualOkManual(UsuarioSoporte quien, String justificacion,
            Map<String, String> datos);

    /**
     * Devuelve la lista de auditoría con una nueva entrada añadida al final,
     * sin mutar {@code this}: las subclases la usan para construir la
     * instancia siguiente cuando una transición deja rastro de auditoría.
     */
    protected final List<AuditoriaIntervencion> auditar(UsuarioSoporte quien, String accion, String detalle) {
        var nueva = new ArrayList<>(auditoria);
        nueva.add(AuditoriaIntervencion.de(quien, accion, detalle));
        return List.copyOf(nueva);
    }

    public final OrdenId id() { return id; }
    public final ExternalId externalId() { return externalId; }
    public final E estado() { return estado; }
    public final List<AuditoriaIntervencion> auditoria() { return auditoria; }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Proceso<?> that = (Proceso<?>) o;
        return Objects.equals(id, that.id)
                && Objects.equals(externalId, that.externalId)
                && Objects.equals(estado, that.estado)
                && Objects.equals(auditoria, that.auditoria);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id, externalId, estado, auditoria);
    }
}
