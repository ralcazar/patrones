package com.ejemplo.app.business.sagas.dominio.sagasecundaria3;

import java.util.List;
import java.util.Map;

import org.jmolecules.ddd.annotation.Entity;

import com.ejemplo.app.business.ordermanager.dominio.AuditoriaIntervencion;
import com.ejemplo.app.business.ordermanager.dominio.ComandoPaso;
import com.ejemplo.app.business.sagas.dominio.comun.ContextoArranque;
import com.ejemplo.app.business.ordermanager.dominio.ExternalId;
import com.ejemplo.app.business.ordermanager.dominio.PasoNoIntervenibleException;
import com.ejemplo.app.business.sagas.dominio.comun.RefPaso7;
import com.ejemplo.app.business.ordermanager.dominio.ResultadoOrden;
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
 */
@Entity
public final class SagaSecundaria3 extends Proceso<EstadoSagaSecundaria3> {

    public static final TipoOrden TIPO = new TipoOrden("SECUNDARIA3");

    private final RefPaso7 refPaso7;
    private RefEjecucion refEjecucion;

    private SagaSecundaria3(OrdenId id, ExternalId externalId, RefPaso7 refPaso7,
            EstadoSagaSecundaria3 estado) {
        super(id, externalId, estado);
        this.refPaso7 = refPaso7;
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
    public void aplicarYAvanzar(ResultadoPaso resultado) {
        if (!(resultado instanceof ResultadoPasoSecundaria3.Ejecutada(var ref))) {
            throw new IllegalArgumentException("Resultado ajeno a la saga secundaria 3: " + resultado);
        }
        this.refEjecucion = ref;
        estado = EstadoSagaSecundaria3.TERMINADA;
    }

    @Override
    public boolean terminada() {
        return estado == EstadoSagaSecundaria3.TERMINADA;
    }

    @Override
    public ResultadoOrden resultadoFinal() {
        return ResultadoOrden.FINALIZADA_OK;
    }

    @Override
    public void marcarPasoActualOkManual(UsuarioSoporte quien, String justificacion, Map<String, String> datos) {
        if (estado == EstadoSagaSecundaria3.TERMINADA) {
            throw new PasoNoIntervenibleException(id, "no tiene paso pendiente en estado " + estado);
        }
        estado = EstadoSagaSecundaria3.TERMINADA;
        auditar(quien, "MARCAR_OK_MANUAL", "EJECUCION: " + justificacion);
    }

    public RefPaso7 refPaso7() { return refPaso7; }
    public RefEjecucion refEjecucion() { return refEjecucion; }
}
