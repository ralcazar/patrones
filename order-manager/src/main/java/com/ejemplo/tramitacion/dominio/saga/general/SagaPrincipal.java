package com.ejemplo.tramitacion.dominio.saga.general;

import com.ejemplo.tramitacion.dominio.saga.paso1.DatoNegocio3;
import com.ejemplo.tramitacion.dominio.saga.paso7.DatoNegocio2;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.List;

/**
 * Saga principal: PASO1 -> PASO2 -> ... -> PASO8, todos síncronos.
 *
 * Reglas de negocio que este agregado encapsula:
 * - Punto de no retorno: cuando PASO7 completa, la saga ya no es cancelable.
 * - Cancelación (solo pre-PASO7): compensa únicamente PASO2 y PASO1, en orden inverso.
 * - Fallos: backoff exponencial hasta 10 intentos; después, ticket a soporte.
 * - Al completar PASO8: la saga queda COMPLETADA y decide arrancar otras 3
 *   sagas (ASINCRONA, SECUENCIAL, SIMPLE), independientes entre sí y sin join.
 */
public final class SagaPrincipal extends Saga {

    private static final EnumSet<Paso> PASOS_PROPIOS = EnumSet.range(Paso.PASO1, Paso.PASO8);

    /** Pasos cuyo resultado consumen pasos posteriores u otras sagas: marcar-OK manual exige aportar datos. */
    private static final EnumSet<Paso> REQUIEREN_DATOS_MANUALES =
            EnumSet.of(Paso.PASO1, Paso.PASO2, Paso.PASO4, Paso.PASO5, Paso.PASO7);

    private final ContextoTramitacion ctx;

    private SagaPrincipal(SagaId id, ContextoTramitacion ctx, EnumSet<Paso> misPasos,
                          EstadoSaga estado, long version) {
        super(id, misPasos, estado, version);
        this.ctx = ctx;
    }

    private SagaPrincipal(SagaId id, ContextoTramitacion ctx, EnumMap<Paso, EjecucionPaso> pasos,
                          List<AuditoriaIntervencion> auditoria, EstadoSaga estado, long version) {
        super(id, pasos, auditoria, estado, version);
        this.ctx = ctx;
    }

    public static SagaPrincipal crear(SagaId id, DatoNegocio1Id datoNegocio1Id,
                                      DatoNegocio3 datos, DatoNegocio2 datoNegocio2) {
        return new SagaPrincipal(id, ContextoTramitacion.inicial(datoNegocio1Id, datos, datoNegocio2),
                PASOS_PROPIOS, EstadoSaga.INICIADA, 0L);
    }

    /** Para el adaptador de persistencia. */
    public static SagaPrincipal rehidratar(SagaId id, ContextoTramitacion ctx,
            EnumMap<Paso, EjecucionPaso> pasos, EstadoSaga estado,
            List<AuditoriaIntervencion> auditoria, long version) {
        return new SagaPrincipal(id, ctx, pasos, auditoria, estado, version);
    }

    // ------------------------------------------------------------------
    // Especialización del ciclo de vida común de Saga
    // ------------------------------------------------------------------

    @Override public TipoSaga tipo() { return TipoSaga.PRINCIPAL; }

    @Override public DatoNegocio1Id datoNegocio1Id() { return ctx.datoNegocio1Id(); }

    @Override protected Paso pasoInicial() { return Paso.PASO1; }

    @Override
    protected ComandoPaso comandoPara(Paso p) {
        return switch (p) {
            case PASO1 -> new ComandoPaso.EjecutarPaso1(ctx.datoNegocio1Id(), ctx.datoNegocio3());
            case PASO2 -> new ComandoPaso.EjecutarPaso2(ctx.refPaso1());
            case PASO3 -> new ComandoPaso.EjecutarPaso3(ctx.datoNegocio1Id(), ctx.refPaso2());
            case PASO4 -> new ComandoPaso.EjecutarPaso4(ctx.refPaso1(), ctx.refPaso2());
            case PASO5 -> new ComandoPaso.EjecutarPaso5(ctx.refPaso4());
            case PASO6 -> new ComandoPaso.EjecutarPaso6(ctx.refPaso5());
            case PASO7 -> new ComandoPaso.EjecutarPaso7(ctx.refPaso5(), ctx.datoNegocio2());
            case PASO8 -> new ComandoPaso.EjecutarPaso8(ctx.datoNegocio1Id(), ctx.refPaso7());
            case ASINCRONO, SECUENCIAL1, SECUENCIAL2, SIMPLE ->
                    throw new IllegalArgumentException("Paso ajeno a la saga principal: " + p);
        };
    }

    @Override
    protected void aplicarResultado(Paso paso, ResultadoPaso resultado) {
        ctx.aplicar(resultado);
    }

    @Override
    protected List<Decision> transicionTras(Paso paso) {
        return switch (paso) {
            case PASO1 -> solicitar(Paso.PASO2);
            case PASO2 -> solicitar(Paso.PASO3);
            case PASO3 -> solicitar(Paso.PASO4);
            case PASO4 -> solicitar(Paso.PASO5);
            case PASO5 -> solicitar(Paso.PASO6);
            case PASO6 -> solicitar(Paso.PASO7);
            case PASO7 -> solicitar(Paso.PASO8); // punto de no retorno alcanzado
            case PASO8 -> completarYArrancarSagas();
            case ASINCRONO, SECUENCIAL1, SECUENCIAL2, SIMPLE ->
                    throw new IllegalArgumentException("Paso ajeno a la saga principal: " + paso);
        };
    }

    @Override
    protected EnumSet<Paso> pasosConDatosManualesObligatorios() {
        return REQUIEREN_DATOS_MANUALES;
    }

    /**
     * Al completar el PASO8 la saga muere y decide arrancar las 3 sagas
     * sucesoras, cada una con su contexto recortado. Se crean en la MISMA
     * transacción; a partir de ahí son sagas independientes.
     */
    private List<Decision> completarYArrancarSagas() {
        var decisiones = new ArrayList<Decision>(List.of(
                new Decision.ArrancarSaga(new ContextoArranque.ContextoAsincrona(
                        ctx.datoNegocio1Id(), ctx.refPaso5())),
                new Decision.ArrancarSaga(new ContextoArranque.ContextoSecuencial(
                        ctx.datoNegocio1Id(), ctx.refPaso1())),
                new Decision.ArrancarSaga(new ContextoArranque.ContextoSimple(
                        ctx.datoNegocio1Id(), ctx.refPaso7()))));
        decisiones.addAll(finalizar());
        return List.copyOf(decisiones);
    }

    // ------------------------------------------------------------------
    // Intervenciones de soporte exclusivas de la principal
    // ------------------------------------------------------------------

    @Override
    public boolean esCancelable() {
        return estado != EstadoSaga.COMPLETADA
                && estado != EstadoSaga.CANCELADA
                && !puntoNoRetornoAlcanzado();
    }

    private boolean puntoNoRetornoAlcanzado() {
        return pasos.get(Paso.PASO7).estado().cuentaComoCompletado();
    }

    /** Cancela la saga. Solo posible ANTES de que PASO7 complete. Compensa PASO2 y PASO1. */
    public List<Decision> cancelarPorSoporte(UsuarioSoporte quien, String motivo) {
        if (estado == EstadoSaga.COMPLETADA) {
            throw new SagaYaCompletadaException(id);
        }
        if (estado == EstadoSaga.CANCELADA) {
            return List.of(); // idempotente
        }
        if (puntoNoRetornoAlcanzado()) {
            throw new PuntoNoRetornoSuperadoException(id);
        }

        estado = EstadoSaga.CANCELADA;
        auditoria.add(AuditoriaIntervencion.de(quien, "CANCELAR", motivo));
        pasos.values().stream()
                .filter(ep -> ep.estado().esActivo())
                .forEach(EjecucionPaso::cancelar);

        var decisiones = new ArrayList<Decision>();
        if (completado(Paso.PASO2)) {
            decisiones.add(new Decision.Compensar(Paso.PASO2, new ComandoPaso.CompensarPaso2(ctx.refPaso2())));
        }
        if (completado(Paso.PASO1)) {
            decisiones.add(new Decision.Compensar(Paso.PASO1, new ComandoPaso.CompensarPaso1(ctx.refPaso1())));
        }
        decisiones.add(new Decision.PublicarEvento(new EventoTramitacion.TramitacionCancelada(id, motivo)));
        return List.copyOf(decisiones);
    }

    // ------------------------------------------------------------------
    // Resultado de las compensaciones (las ejecuta aplicación y reporta aquí)
    // ------------------------------------------------------------------

    public void compensacionCompletada(Paso paso) {
        pasoDe(paso).compensado();
    }

    public List<Decision> compensacionFallida(Paso paso, MotivoFallo motivo) {
        // Una compensación que falla deja inconsistencia real: ticket inmediato.
        return List.of(new Decision.AbrirTicketSoporte(
                id, TipoSaga.PRINCIPAL, paso, motivo, pasoDe(paso).intentos(), false));
    }

    // ------------------------------------------------------------------
    // Helpers y lectura
    // ------------------------------------------------------------------

    private boolean completado(Paso p) {
        return pasos.get(p).estado().cuentaComoCompletado();
    }

    public ContextoTramitacion contexto() { return ctx; }
}
