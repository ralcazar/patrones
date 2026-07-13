package com.ejemplo.tramitacion.dominio.saga.general;

import com.ejemplo.tramitacion.dominio.saga.paso1.RefPaso1;
import com.ejemplo.tramitacion.dominio.saga.secuencial.RefSecuencial1;
import com.ejemplo.tramitacion.dominio.saga.secuencial.RefSecuencial2;

import java.util.EnumMap;
import java.util.EnumSet;
import java.util.List;

/**
 * Saga SECUENCIAL: dos pasos secuenciales síncronos, SECUENCIAL1 -> SECUENCIAL2.
 * SECUENCIAL2 consume la referencia que produce SECUENCIAL1, por eso marcar-OK
 * manual de SECUENCIAL1 exige aportar esa referencia.
 */
public final class SagaSecuencial extends SagaSucesora {

    private final RefPaso1 refPaso1;
    private RefSecuencial1 refSecuencial1;   // lo produce SECUENCIAL1, lo consume SECUENCIAL2
    private RefSecuencial2 refSecuencial2;

    private SagaSecuencial(SagaId id, DatoNegocio1Id datoNegocio1Id, RefPaso1 refPaso1) {
        super(id, datoNegocio1Id, EnumSet.of(Paso.SECUENCIAL1, Paso.SECUENCIAL2), EstadoSaga.INICIADA, 0L);
        this.refPaso1 = refPaso1;
    }

    private SagaSecuencial(SagaId id, DatoNegocio1Id datoNegocio1Id, RefPaso1 refPaso1,
                           RefSecuencial1 refSecuencial1, RefSecuencial2 refSecuencial2,
                           EnumMap<Paso, EjecucionPaso> pasos, List<AuditoriaIntervencion> auditoria,
                           EstadoSaga estado, long version) {
        super(id, datoNegocio1Id, pasos, auditoria, estado, version);
        this.refPaso1 = refPaso1;
        this.refSecuencial1 = refSecuencial1;
        this.refSecuencial2 = refSecuencial2;
    }

    public static SagaSecuencial crear(SagaId id, ContextoArranque.ContextoSecuencial ctx) {
        return new SagaSecuencial(id, ctx.datoNegocio1Id(), ctx.refPaso1());
    }

    public static SagaSecuencial rehidratar(SagaId id, DatoNegocio1Id datoNegocio1Id,
            RefPaso1 refPaso1, RefSecuencial1 refSecuencial1, RefSecuencial2 refSecuencial2,
            EnumMap<Paso, EjecucionPaso> pasos, List<AuditoriaIntervencion> auditoria,
            EstadoSaga estado, long version) {
        return new SagaSecuencial(id, datoNegocio1Id, refPaso1, refSecuencial1, refSecuencial2,
                pasos, auditoria, estado, version);
    }

    @Override public TipoSaga tipo() { return TipoSaga.SECUENCIAL; }

    @Override protected Paso pasoInicial() { return Paso.SECUENCIAL1; }

    @Override
    protected ComandoPaso comandoPara(Paso paso) {
        return switch (paso) {
            case SECUENCIAL1 -> new ComandoPaso.EjecutarSecuencial1(datoNegocio1Id, refPaso1);
            case SECUENCIAL2 -> new ComandoPaso.EjecutarSecuencial2(refSecuencial1);
            default -> throw new IllegalArgumentException("Paso " + paso + " ajeno a la saga SECUENCIAL");
        };
    }

    @Override
    protected void aplicarResultado(Paso paso, ResultadoPaso resultado) {
        switch (resultado) {
            case ResultadoPaso.ResultadoSecuencial1(var ref) -> this.refSecuencial1 = ref;
            case ResultadoPaso.ResultadoSecuencial2(var ref) -> this.refSecuencial2 = ref;
            default -> { /* sin datos que aplicar */ }
        }
    }

    @Override
    protected List<Decision> transicionTras(Paso paso) {
        return switch (paso) {
            case SECUENCIAL1 -> solicitar(Paso.SECUENCIAL2);
            case SECUENCIAL2 -> finalizar();
            default -> throw new IllegalArgumentException("Paso " + paso + " ajeno a la saga SECUENCIAL");
        };
    }

    @Override
    protected EnumSet<Paso> pasosConDatosManualesObligatorios() {
        return EnumSet.of(Paso.SECUENCIAL1); // SECUENCIAL2 necesita refSecuencial1
    }

    public RefPaso1 refPaso1() { return refPaso1; }
    public RefSecuencial1 refSecuencial1() { return refSecuencial1; }
    public RefSecuencial2 refSecuencial2() { return refSecuencial2; }
}
