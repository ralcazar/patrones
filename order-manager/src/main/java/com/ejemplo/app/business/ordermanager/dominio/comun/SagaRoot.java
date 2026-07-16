package com.ejemplo.app.business.ordermanager.dominio.comun;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import org.jmolecules.ddd.annotation.Entity;
import org.jmolecules.ddd.annotation.Identity;

/**
 * Ciclo de vida de NEGOCIO compartido por las 4 sagas: una FSM (el estado E)
 * más la auditoría de intervenciones de soporte. Entidad interna del agregado
 * {@link OrdenRoot}, que es quien la contiene: no conoce reintentos, tiempo
 * ni token de ejecución (eso es responsabilidad de OrdenRoot), ni tiene su
 * propia {@code version} (la controla el agregado).
 */
@Entity
public abstract class SagaRoot<E extends Enum<E>> {

    @Identity
    protected final SagaId id;
    protected final ExternalId externalId;
    protected final List<AuditoriaIntervencion> auditoria;
    protected E estado;

    protected SagaRoot(SagaId id, ExternalId externalId, E estado) {
        this(id, externalId, estado, List.of());
    }

    /** Constructor de rehidratación para el adaptador de persistencia. */
    protected SagaRoot(SagaId id, ExternalId externalId, E estado, List<AuditoriaIntervencion> auditoria) {
        this.id = id;
        this.externalId = externalId;
        this.estado = estado;
        this.auditoria = new ArrayList<>(auditoria);
    }

    public abstract TipoSaga tipo();

    /** Comando del paso en el que está ahora la FSM de negocio. */
    public abstract ComandoPaso comandoActual();

    /** Aplica el resultado del paso actual al contexto de la saga y avanza la FSM. */
    public abstract void aplicarYAvanzar(ResultadoPaso resultado);

    /** La FSM alcanzó un estado final: no queda ningún paso que ejecutar. */
    public abstract boolean terminada();

    /** Solo válido cuando {@link #terminada()} es cierto. */
    public abstract ResultadoOrden resultadoFinal();

    /** Soporte marca OK a mano el paso pendiente actual, aportando los datos que ese paso habría producido. */
    public abstract void marcarPasoActualOkManual(UsuarioSoporte quien, String justificacion,
            Map<String, String> datos);

    protected final void auditar(UsuarioSoporte quien, String accion, String detalle) {
        auditoria.add(AuditoriaIntervencion.de(quien, accion, detalle));
    }

    public final SagaId id() { return id; }
    public final ExternalId externalId() { return externalId; }
    public final E estado() { return estado; }
    public final List<AuditoriaIntervencion> auditoria() { return Collections.unmodifiableList(auditoria); }
}
