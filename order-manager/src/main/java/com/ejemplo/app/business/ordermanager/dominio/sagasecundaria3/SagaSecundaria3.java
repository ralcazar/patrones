package com.ejemplo.app.business.ordermanager.dominio.sagasecundaria3;

import java.util.List;
import java.util.Map;

import org.jmolecules.ddd.annotation.Entity;

import com.ejemplo.app.business.ordermanager.dominio.comun.AuditoriaIntervencion;
import com.ejemplo.app.business.ordermanager.dominio.comun.ComandoPaso;
import com.ejemplo.app.business.ordermanager.dominio.comun.ContextoArranque;
import com.ejemplo.app.business.ordermanager.dominio.comun.ExternalId;
import com.ejemplo.app.business.ordermanager.dominio.comun.PasoNoIntervenibleException;
import com.ejemplo.app.business.ordermanager.dominio.comun.RefPaso7;
import com.ejemplo.app.business.ordermanager.dominio.comun.ResultadoOrden;
import com.ejemplo.app.business.ordermanager.dominio.comun.ResultadoPaso;
import com.ejemplo.app.business.ordermanager.dominio.comun.SagaId;
import com.ejemplo.app.business.ordermanager.dominio.comun.Saga;
import com.ejemplo.app.business.ordermanager.dominio.comun.TipoSaga;
import com.ejemplo.app.business.ordermanager.dominio.comun.UsuarioSoporte;

/**
 * Saga SECUNDARIA3: un único paso síncrono (una llamada REST).
 *
 * Nace cuando la principal alcanza TERMINADA (después del punto de no
 * retorno): nunca se cancela ni compensa.
 */
@Entity
public final class SagaSecundaria3 extends Saga<EstadoSagaSecundaria3> {

    private final RefPaso7 refPaso7;
    private RefEjecucion refEjecucion;

    private SagaSecundaria3(SagaId id, ExternalId externalId, RefPaso7 refPaso7,
            EstadoSagaSecundaria3 estado) {
        super(id, externalId, estado);
        this.refPaso7 = refPaso7;
    }

    private SagaSecundaria3(SagaId id, ExternalId externalId, RefPaso7 refPaso7,
            RefEjecucion refEjecucion, EstadoSagaSecundaria3 estado, List<AuditoriaIntervencion> auditoria) {
        super(id, externalId, estado, auditoria);
        this.refPaso7 = refPaso7;
        this.refEjecucion = refEjecucion;
    }

    public static SagaSecundaria3 crear(SagaId id, ContextoArranque.ArranqueSecundaria3 ctx) {
        return new SagaSecundaria3(id, ctx.externalId(), ctx.refPaso7(), EstadoSagaSecundaria3.INICIAL);
    }

    /** Para el adaptador de persistencia. */
    public static SagaSecundaria3 rehidratar(SagaId id, ExternalId externalId, RefPaso7 refPaso7,
            RefEjecucion refEjecucion, EstadoSagaSecundaria3 estado, List<AuditoriaIntervencion> auditoria) {
        return new SagaSecundaria3(id, externalId, refPaso7, refEjecucion, estado, auditoria);
    }

    @Override public TipoSaga tipo() { return TipoSaga.SECUNDARIA3; }

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
