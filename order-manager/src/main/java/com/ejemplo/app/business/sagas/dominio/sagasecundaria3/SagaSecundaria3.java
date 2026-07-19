package com.ejemplo.app.business.sagas.dominio.sagasecundaria3;

import java.util.List;
import java.util.Map;
import java.util.Objects;

import org.jmolecules.ddd.annotation.ValueObject;

import com.ejemplo.app.business.ordermanager.dominio.AuditoriaIntervencion;
import com.ejemplo.app.business.ordermanager.dominio.ComandoPaso;
import com.ejemplo.app.business.sagas.dominio.comun.ContextoArranque;
import com.ejemplo.app.business.ordermanager.dominio.ExternalId;
import com.ejemplo.app.business.ordermanager.dominio.PasoNoIntervenibleException;
import com.ejemplo.app.business.sagas.dominio.comun.RefPaso7;
import com.ejemplo.app.business.ordermanager.dominio.ResultadoPaso;
import com.ejemplo.app.business.ordermanager.dominio.OrdenId;
import com.ejemplo.app.business.ordermanager.dominio.Proceso;
import com.ejemplo.app.business.ordermanager.dominio.TipoOrden;
import com.ejemplo.app.business.ordermanager.dominio.UsuarioSoporte;

/**
 * Saga SECUNDARIA3: un único paso síncrono (una llamada REST).
 *
 * Nace cuando la principal alcanza TERMINADA (después del punto de no
 * retorno): nunca se cancela ni compensa.
 *
 * Value object inmutable (ver {@link Proceso}): cada transición devuelve una
 * instancia nueva de {@code SagaSecundaria3}, dejando la original intacta.
 */
@ValueObject
public final class SagaSecundaria3 extends Proceso<EstadoSagaSecundaria3> {

    public static final TipoOrden TIPO = new TipoOrden("SECUNDARIA3");

    private final RefPaso7 refPaso7;
    private final RefEjecucion refEjecucion;

    private SagaSecundaria3(OrdenId id, ExternalId externalId, RefPaso7 refPaso7,
            EstadoSagaSecundaria3 estado) {
        this(id, externalId, refPaso7, null, estado, List.of());
    }

    private SagaSecundaria3(OrdenId id, ExternalId externalId, RefPaso7 refPaso7,
            RefEjecucion refEjecucion, EstadoSagaSecundaria3 estado, List<AuditoriaIntervencion> auditoria) {
        super(id, externalId, estado, auditoria);
        this.refPaso7 = refPaso7;
        this.refEjecucion = refEjecucion;
    }

    public static SagaSecundaria3 crear(OrdenId id, ContextoArranque.ArranqueSecundaria3 ctx) {
        return new SagaSecundaria3(id, ctx.externalId(), ctx.refPaso7(), EstadoSagaSecundaria3.INICIAL);
    }

    /** Para el adaptador de persistencia. */
    public static SagaSecundaria3 rehidratar(OrdenId id, ExternalId externalId, RefPaso7 refPaso7,
            RefEjecucion refEjecucion, EstadoSagaSecundaria3 estado, List<AuditoriaIntervencion> auditoria) {
        return new SagaSecundaria3(id, externalId, refPaso7, refEjecucion, estado, auditoria);
    }

    @Override public TipoOrden tipo() { return TIPO; }

    @Override
    public ComandoPaso comandoActual() {
        if (estado != EstadoSagaSecundaria3.INICIAL) {
            throw new IllegalStateException(
                    "La saga " + id.valor() + " no tiene paso pendiente en estado " + estado);
        }
        return new ComandoPasoSecundaria3.Ejecutar(externalId, refPaso7);
    }

    @Override
    public SagaSecundaria3 aplicarYAvanzar(ResultadoPaso resultado) {
        if (!(resultado instanceof ResultadoPasoSecundaria3.Ejecutada(var ref))) {
            throw new IllegalArgumentException("Resultado ajeno a la saga secundaria 3: " + resultado);
        }
        return new SagaSecundaria3(id, externalId, refPaso7, ref, EstadoSagaSecundaria3.TERMINADA, auditoria);
    }

    @Override
    public boolean terminada() {
        return estado == EstadoSagaSecundaria3.TERMINADA;
    }

    @Override
    public SagaSecundaria3 marcarPasoActualOkManual(UsuarioSoporte quien, String justificacion,
            Map<String, String> datos) {
        if (estado == EstadoSagaSecundaria3.TERMINADA) {
            throw new PasoNoIntervenibleException(id, "no tiene paso pendiente en estado " + estado);
        }
        var nuevaAuditoria = auditar(quien, "MARCAR_OK_MANUAL", "EJECUCION: " + justificacion);
        return new SagaSecundaria3(id, externalId, refPaso7, refEjecucion, EstadoSagaSecundaria3.TERMINADA,
                nuevaAuditoria);
    }

    public RefPaso7 refPaso7() { return refPaso7; }
    public RefEjecucion refEjecucion() { return refEjecucion; }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!super.equals(o)) return false;
        SagaSecundaria3 that = (SagaSecundaria3) o;
        return Objects.equals(refPaso7, that.refPaso7)
                && Objects.equals(refEjecucion, that.refEjecucion);
    }

    @Override
    public int hashCode() {
        return Objects.hash(super.hashCode(), refPaso7, refEjecucion);
    }
}
