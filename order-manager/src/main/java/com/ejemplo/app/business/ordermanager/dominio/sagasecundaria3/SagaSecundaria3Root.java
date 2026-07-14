package com.ejemplo.app.business.ordermanager.dominio.sagasecundaria3;

import java.time.Instant;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.List;

import org.jmolecules.ddd.annotation.AggregateRoot;

import com.ejemplo.app.business.ordermanager.dominio.comun.AuditoriaIntervencion;
import com.ejemplo.app.business.ordermanager.dominio.comun.ComandoPaso;
import com.ejemplo.app.business.ordermanager.dominio.comun.ContextoArranque;
import com.ejemplo.app.business.ordermanager.dominio.comun.Decision;
import com.ejemplo.app.business.ordermanager.dominio.comun.EjecucionPaso;
import com.ejemplo.app.business.ordermanager.dominio.comun.EstadoSaga;
import com.ejemplo.app.business.ordermanager.dominio.comun.EstadoTicket;
import com.ejemplo.app.business.ordermanager.dominio.comun.ExternalId;
import com.ejemplo.app.business.ordermanager.dominio.comun.RefPaso7;
import com.ejemplo.app.business.ordermanager.dominio.comun.ResultadoPaso;
import com.ejemplo.app.business.ordermanager.dominio.comun.Saga;
import com.ejemplo.app.business.ordermanager.dominio.comun.SagaId;
import com.ejemplo.app.business.ordermanager.dominio.comun.TipoSaga;

/**
 * Saga SECUNDARIA3: un único paso síncrono (una llamada REST) y COMPLETADA.
 *
 * Nace cuando la principal completa (después del punto de no retorno):
 * nunca se cancela ni compensa.
 */
@AggregateRoot
public final class SagaSecundaria3Root extends Saga<PasoSagaSecundaria3> {

    private final RefPaso7 refPaso7;
    private RefEjecucion refEjecucion;

    private SagaSecundaria3Root(SagaId id, ExternalId externalId, RefPaso7 refPaso7) {
        super(id, externalId, PasoSagaSecundaria3.class,
                EnumSet.allOf(PasoSagaSecundaria3.class), EstadoSaga.INICIADA, 0L);
        this.refPaso7 = refPaso7;
    }

    private SagaSecundaria3Root(SagaId id, ExternalId externalId, RefPaso7 refPaso7,
            RefEjecucion refEjecucion,
            EnumMap<PasoSagaSecundaria3, EjecucionPaso<PasoSagaSecundaria3>> pasos,
            List<AuditoriaIntervencion> auditoria, EstadoSaga estado,
            EstadoTicket estadoTicket, Instant ticketAbiertoEn, long version) {
        super(id, externalId, PasoSagaSecundaria3.class, pasos, auditoria, estado,
                estadoTicket, ticketAbiertoEn, version);
        this.refPaso7 = refPaso7;
        this.refEjecucion = refEjecucion;
    }

    public static SagaSecundaria3Root crear(SagaId id, ContextoArranque.ArranqueSecundaria3 ctx) {
        return new SagaSecundaria3Root(id, ctx.externalId(), ctx.refPaso7());
    }

    /** Para el adaptador de persistencia. */
    public static SagaSecundaria3Root rehidratar(SagaId id, ExternalId externalId,
            RefPaso7 refPaso7, RefEjecucion refEjecucion,
            EnumMap<PasoSagaSecundaria3, EjecucionPaso<PasoSagaSecundaria3>> pasos,
            List<AuditoriaIntervencion> auditoria, EstadoSaga estado,
            EstadoTicket estadoTicket, Instant ticketAbiertoEn, long version) {
        return new SagaSecundaria3Root(id, externalId, refPaso7, refEjecucion,
                pasos, auditoria, estado, estadoTicket, ticketAbiertoEn, version);
    }

    @Override public TipoSaga tipo() { return TipoSaga.SECUNDARIA3; }

    @Override protected PasoSagaSecundaria3 pasoInicial() { return PasoSagaSecundaria3.EJECUCION; }

    @Override
    protected ComandoPaso comandoPara(PasoSagaSecundaria3 paso) {
        return new ComandoPasoSecundaria3.Ejecutar(externalId, refPaso7);
    }

    @Override
    protected void aplicarResultado(PasoSagaSecundaria3 paso, ResultadoPaso resultado) {
        if (resultado instanceof ResultadoPasoSecundaria3.Ejecutada(var ref)) {
            this.refEjecucion = ref;
        }
    }

    @Override
    protected List<Decision<PasoSagaSecundaria3>> transicionTras(PasoSagaSecundaria3 paso) {
        return finalizar();
    }

    public RefPaso7 refPaso7() { return refPaso7; }
    public RefEjecucion refEjecucion() { return refEjecucion; }
}
