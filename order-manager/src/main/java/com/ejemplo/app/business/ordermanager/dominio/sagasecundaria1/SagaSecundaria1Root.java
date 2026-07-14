package com.ejemplo.app.business.ordermanager.dominio.sagasecundaria1;

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
import com.ejemplo.app.business.ordermanager.dominio.comun.RefPaso1;
import com.ejemplo.app.business.ordermanager.dominio.comun.ResultadoPaso;
import com.ejemplo.app.business.ordermanager.dominio.comun.Saga;
import com.ejemplo.app.business.ordermanager.dominio.comun.SagaId;
import com.ejemplo.app.business.ordermanager.dominio.comun.TipoSaga;

/**
 * Saga SECUNDARIA1: dos pasos síncronos encadenados, INICIO -> CONFIRMACION,
 * cada uno una llamada REST a un método distinto del mismo servicio.
 * CONFIRMACION consume la referencia que produce INICIO, por eso marcar-OK
 * manual de INICIO exige aportar esa referencia.
 *
 * Nace cuando la principal completa (después del punto de no retorno):
 * nunca se cancela ni compensa.
 */
@AggregateRoot
public final class SagaSecundaria1Root extends Saga<PasoSagaSecundaria1> {

    private final RefPaso1 refPaso1;
    private RefInicio refInicio;   // lo produce INICIO, lo consume CONFIRMACION
    private RefConfirmacion refConfirmacion;

    private SagaSecundaria1Root(SagaId id, ExternalId externalId, RefPaso1 refPaso1) {
        super(id, externalId, PasoSagaSecundaria1.class,
                EnumSet.allOf(PasoSagaSecundaria1.class), EstadoSaga.INICIADA, 0L);
        this.refPaso1 = refPaso1;
    }

    private SagaSecundaria1Root(SagaId id, ExternalId externalId, RefPaso1 refPaso1,
            RefInicio refInicio, RefConfirmacion refConfirmacion,
            EnumMap<PasoSagaSecundaria1, EjecucionPaso<PasoSagaSecundaria1>> pasos,
            List<AuditoriaIntervencion> auditoria, EstadoSaga estado,
            EstadoTicket estadoTicket, Instant ticketAbiertoEn, long version) {
        super(id, externalId, PasoSagaSecundaria1.class, pasos, auditoria, estado,
                estadoTicket, ticketAbiertoEn, version);
        this.refPaso1 = refPaso1;
        this.refInicio = refInicio;
        this.refConfirmacion = refConfirmacion;
    }

    public static SagaSecundaria1Root crear(SagaId id, ContextoArranque.ArranqueSecundaria1 ctx) {
        return new SagaSecundaria1Root(id, ctx.externalId(), ctx.refPaso1());
    }

    /** Para el adaptador de persistencia. */
    public static SagaSecundaria1Root rehidratar(SagaId id, ExternalId externalId,
            RefPaso1 refPaso1, RefInicio refInicio, RefConfirmacion refConfirmacion,
            EnumMap<PasoSagaSecundaria1, EjecucionPaso<PasoSagaSecundaria1>> pasos,
            List<AuditoriaIntervencion> auditoria, EstadoSaga estado,
            EstadoTicket estadoTicket, Instant ticketAbiertoEn, long version) {
        return new SagaSecundaria1Root(id, externalId, refPaso1, refInicio, refConfirmacion,
                pasos, auditoria, estado, estadoTicket, ticketAbiertoEn, version);
    }

    @Override public TipoSaga tipo() { return TipoSaga.SECUNDARIA1; }

    @Override protected PasoSagaSecundaria1 pasoInicial() { return PasoSagaSecundaria1.INICIO; }

    @Override
    protected ComandoPaso comandoPara(PasoSagaSecundaria1 paso) {
        return switch (paso) {
            case INICIO -> new ComandoPasoSecundaria1.Iniciar(externalId, refPaso1);
            case CONFIRMACION -> new ComandoPasoSecundaria1.Confirmar(refInicio);
        };
    }

    @Override
    protected void aplicarResultado(PasoSagaSecundaria1 paso, ResultadoPaso resultado) {
        switch (resultado) {
            case ResultadoPasoSecundaria1.Iniciada(var ref) -> this.refInicio = ref;
            case ResultadoPasoSecundaria1.Confirmada(var ref) -> this.refConfirmacion = ref;
            default -> { /* sin datos que aplicar */ }
        }
    }

    @Override
    protected List<Decision<PasoSagaSecundaria1>> transicionTras(PasoSagaSecundaria1 paso) {
        return switch (paso) {
            case INICIO -> solicitar(PasoSagaSecundaria1.CONFIRMACION);
            case CONFIRMACION -> finalizar();
        };
    }

    @Override
    protected EnumSet<PasoSagaSecundaria1> pasosConDatosManualesObligatorios() {
        return EnumSet.of(PasoSagaSecundaria1.INICIO); // CONFIRMACION necesita refInicio
    }

    public RefPaso1 refPaso1() { return refPaso1; }
    public RefInicio refInicio() { return refInicio; }
    public RefConfirmacion refConfirmacion() { return refConfirmacion; }
}
