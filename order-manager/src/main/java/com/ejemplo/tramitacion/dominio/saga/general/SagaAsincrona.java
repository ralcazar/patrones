package com.ejemplo.tramitacion.dominio.saga.general;

import com.ejemplo.tramitacion.dominio.saga.asincrono.RefAsincrono;
import com.ejemplo.tramitacion.dominio.saga.paso5.RefPaso5;

import java.util.EnumMap;
import java.util.EnumSet;
import java.util.List;

/**
 * Saga ASINCRONA: un único paso ASÍNCRONO (comando por Kafka, resultado por evento).
 * El agregado no sabe que es asíncrono: eso lo resuelven el adaptador y el consumer.
 */
public final class SagaAsincrona extends SagaSucesora {

    private final RefPaso5 refPaso5;
    private RefAsincrono refAsincrono;

    private SagaAsincrona(SagaId id, DatoNegocio1Id datoNegocio1Id, RefPaso5 refPaso5) {
        super(id, datoNegocio1Id, EnumSet.of(Paso.ASINCRONO), EstadoSaga.INICIADA, 0L);
        this.refPaso5 = refPaso5;
    }

    private SagaAsincrona(SagaId id, DatoNegocio1Id datoNegocio1Id, RefPaso5 refPaso5,
                          RefAsincrono refAsincrono, EnumMap<Paso, EjecucionPaso> pasos,
                          List<AuditoriaIntervencion> auditoria, EstadoSaga estado, long version) {
        super(id, datoNegocio1Id, pasos, auditoria, estado, version);
        this.refPaso5 = refPaso5;
        this.refAsincrono = refAsincrono;
    }

    public static SagaAsincrona crear(SagaId id, ContextoArranque.ContextoAsincrona ctx) {
        return new SagaAsincrona(id, ctx.datoNegocio1Id(), ctx.refPaso5());
    }

    public static SagaAsincrona rehidratar(SagaId id, DatoNegocio1Id datoNegocio1Id,
            RefPaso5 refPaso5, RefAsincrono refAsincrono, EnumMap<Paso, EjecucionPaso> pasos,
            List<AuditoriaIntervencion> auditoria, EstadoSaga estado, long version) {
        return new SagaAsincrona(id, datoNegocio1Id, refPaso5, refAsincrono,
                pasos, auditoria, estado, version);
    }

    @Override public TipoSaga tipo() { return TipoSaga.ASINCRONA; }

    @Override protected Paso pasoInicial() { return Paso.ASINCRONO; }

    @Override
    protected ComandoPaso comandoPara(Paso paso) {
        return new ComandoPaso.EjecutarAsincrono(datoNegocio1Id, refPaso5);
    }

    @Override
    protected void aplicarResultado(Paso paso, ResultadoPaso resultado) {
        if (resultado instanceof ResultadoPaso.ResultadoAsincrono(var ref)) {
            this.refAsincrono = ref;
        }
    }

    @Override
    protected List<Decision> transicionTras(Paso paso) {
        return finalizar();
    }

    public RefPaso5 refPaso5() { return refPaso5; }
    public RefAsincrono refAsincrono() { return refAsincrono; }
}
