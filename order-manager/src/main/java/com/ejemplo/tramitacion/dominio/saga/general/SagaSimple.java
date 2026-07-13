package com.ejemplo.tramitacion.dominio.saga.general;

import com.ejemplo.tramitacion.dominio.saga.paso7.RefPaso7;
import com.ejemplo.tramitacion.dominio.saga.simple.RefSimple;

import java.util.EnumMap;
import java.util.EnumSet;
import java.util.List;

/** Saga SIMPLE: un único paso síncrono. */
public final class SagaSimple extends SagaSucesora {

    private final RefPaso7 refPaso7;
    private RefSimple refSimple;

    private SagaSimple(SagaId id, DatoNegocio1Id datoNegocio1Id, RefPaso7 refPaso7) {
        super(id, datoNegocio1Id, EnumSet.of(Paso.SIMPLE), EstadoSaga.INICIADA, 0L);
        this.refPaso7 = refPaso7;
    }

    private SagaSimple(SagaId id, DatoNegocio1Id datoNegocio1Id, RefPaso7 refPaso7,
                       RefSimple refSimple, EnumMap<Paso, EjecucionPaso> pasos,
                       List<AuditoriaIntervencion> auditoria, EstadoSaga estado, long version) {
        super(id, datoNegocio1Id, pasos, auditoria, estado, version);
        this.refPaso7 = refPaso7;
        this.refSimple = refSimple;
    }

    public static SagaSimple crear(SagaId id, ContextoArranque.ContextoSimple ctx) {
        return new SagaSimple(id, ctx.datoNegocio1Id(), ctx.refPaso7());
    }

    public static SagaSimple rehidratar(SagaId id, DatoNegocio1Id datoNegocio1Id,
            RefPaso7 refPaso7, RefSimple refSimple, EnumMap<Paso, EjecucionPaso> pasos,
            List<AuditoriaIntervencion> auditoria, EstadoSaga estado, long version) {
        return new SagaSimple(id, datoNegocio1Id, refPaso7, refSimple,
                pasos, auditoria, estado, version);
    }

    @Override public TipoSaga tipo() { return TipoSaga.SIMPLE; }

    @Override protected Paso pasoInicial() { return Paso.SIMPLE; }

    @Override
    protected ComandoPaso comandoPara(Paso paso) {
        return new ComandoPaso.EjecutarSimple(datoNegocio1Id, refPaso7);
    }

    @Override
    protected void aplicarResultado(Paso paso, ResultadoPaso resultado) {
        if (resultado instanceof ResultadoPaso.ResultadoSimple(var ref)) {
            this.refSimple = ref;
        }
    }

    @Override
    protected List<Decision> transicionTras(Paso paso) {
        return finalizar();
    }

    public RefPaso7 refPaso7() { return refPaso7; }
    public RefSimple refSimple() { return refSimple; }
}
