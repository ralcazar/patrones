package com.ejemplo.app.business.ordermanager.dominio.sagaprincipal;

import java.util.ArrayList;
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
import com.ejemplo.app.business.ordermanager.dominio.comun.ExternalId;
import com.ejemplo.app.business.ordermanager.dominio.comun.MotivoFallo;
import com.ejemplo.app.business.ordermanager.dominio.comun.PuntoNoRetornoSuperadoException;
import com.ejemplo.app.business.ordermanager.dominio.comun.ResultadoPaso;
import com.ejemplo.app.business.ordermanager.dominio.comun.Saga;
import com.ejemplo.app.business.ordermanager.dominio.comun.SagaId;
import com.ejemplo.app.business.ordermanager.dominio.comun.SagaYaCompletadaException;
import com.ejemplo.app.business.ordermanager.dominio.comun.TipoSaga;
import com.ejemplo.app.business.ordermanager.dominio.comun.UsuarioSoporte;

/**
 * Saga principal: PASO1 -> PASO2 -> ... -> PASO8, todos síncronos.
 *
 * Reglas de negocio que este agregado encapsula:
 * - Punto de no retorno: cuando PASO7 completa, la saga ya no es cancelable.
 * - Cancelación (solo pre-PASO7): compensa únicamente PASO2 y PASO1, en orden
 *   inverso. La cancelación queda reflejada en el estado y la auditoría; no
 *   se publica ningún evento.
 * - Fallos: backoff exponencial hasta 10 intentos; después, ticket a soporte.
 * - Al completar PASO8: la saga queda COMPLETADA y decide arrancar las 3 sagas
 *   secundarias (SECUNDARIA1, SECUNDARIA2, SECUNDARIA3), independientes entre
 *   sí y sin join.
 */
@AggregateRoot
public final class SagaPrincipalRoot extends Saga<PasoSagaPrincipal> {

    private static final EnumSet<PasoSagaPrincipal> PASOS_PROPIOS =
            EnumSet.allOf(PasoSagaPrincipal.class);

    /** Pasos cuyo resultado consumen pasos posteriores u otras sagas: marcar-OK manual exige aportar datos. */
    private static final EnumSet<PasoSagaPrincipal> REQUIEREN_DATOS_MANUALES = EnumSet.of(
            PasoSagaPrincipal.PASO1, PasoSagaPrincipal.PASO2, PasoSagaPrincipal.PASO4,
            PasoSagaPrincipal.PASO5, PasoSagaPrincipal.PASO7);

    private final ContextoTramitacion ctx;

    private SagaPrincipalRoot(SagaId id, ExternalId externalId, ContextoTramitacion ctx,
                              EnumSet<PasoSagaPrincipal> misPasos, EstadoSaga estado, long version) {
        super(id, externalId, PasoSagaPrincipal.class, misPasos, estado, version);
        this.ctx = ctx;
    }

    private SagaPrincipalRoot(SagaId id, ExternalId externalId, ContextoTramitacion ctx,
                              EnumMap<PasoSagaPrincipal, EjecucionPaso<PasoSagaPrincipal>> pasos,
                              List<AuditoriaIntervencion> auditoria, EstadoSaga estado, long version) {
        super(id, externalId, PasoSagaPrincipal.class, pasos, auditoria, estado, version);
        this.ctx = ctx;
    }

    public static SagaPrincipalRoot crear(SagaId id, ExternalId externalId,
                                          DatoNegocio3 datos, DatoNegocio2 datoNegocio2) {
        return new SagaPrincipalRoot(id, externalId, ContextoTramitacion.inicial(datos, datoNegocio2),
                PASOS_PROPIOS, EstadoSaga.INICIADA, 0L);
    }

    /** Para el adaptador de persistencia. */
    public static SagaPrincipalRoot rehidratar(SagaId id, ExternalId externalId, ContextoTramitacion ctx,
            EnumMap<PasoSagaPrincipal, EjecucionPaso<PasoSagaPrincipal>> pasos, EstadoSaga estado,
            List<AuditoriaIntervencion> auditoria, long version) {
        return new SagaPrincipalRoot(id, externalId, ctx, pasos, auditoria, estado, version);
    }

    // ------------------------------------------------------------------
    // Especialización del ciclo de vida común de Saga
    // ------------------------------------------------------------------

    @Override public TipoSaga tipo() { return TipoSaga.PRINCIPAL; }

    @Override protected PasoSagaPrincipal pasoInicial() { return PasoSagaPrincipal.PASO1; }

    @Override
    protected ComandoPaso comandoPara(PasoSagaPrincipal p) {
        return switch (p) {
            case PASO1 -> new ComandoPasoPrincipal.EjecutarPaso1(externalId, ctx.datoNegocio3());
            case PASO2 -> new ComandoPasoPrincipal.EjecutarPaso2(ctx.refPaso1());
            case PASO3 -> new ComandoPasoPrincipal.EjecutarPaso3(externalId, ctx.refPaso2());
            case PASO4 -> new ComandoPasoPrincipal.EjecutarPaso4(ctx.refPaso1(), ctx.refPaso2());
            case PASO5 -> new ComandoPasoPrincipal.EjecutarPaso5(ctx.refPaso4());
            case PASO6 -> new ComandoPasoPrincipal.EjecutarPaso6(ctx.refPaso5());
            case PASO7 -> new ComandoPasoPrincipal.EjecutarPaso7(ctx.refPaso5(), ctx.datoNegocio2());
            case PASO8 -> new ComandoPasoPrincipal.EjecutarPaso8(externalId, ctx.refPaso7());
        };
    }

    @Override
    protected void aplicarResultado(PasoSagaPrincipal paso, ResultadoPaso resultado) {
        if (!(resultado instanceof ResultadoPasoPrincipal r)) {
            throw new IllegalArgumentException("Resultado ajeno a la saga principal: " + resultado);
        }
        ctx.aplicar(r);
    }

    @Override
    protected List<Decision<PasoSagaPrincipal>> transicionTras(PasoSagaPrincipal paso) {
        return switch (paso) {
            case PASO1 -> solicitar(PasoSagaPrincipal.PASO2);
            case PASO2 -> solicitar(PasoSagaPrincipal.PASO3);
            case PASO3 -> solicitar(PasoSagaPrincipal.PASO4);
            case PASO4 -> solicitar(PasoSagaPrincipal.PASO5);
            case PASO5 -> solicitar(PasoSagaPrincipal.PASO6);
            case PASO6 -> solicitar(PasoSagaPrincipal.PASO7);
            case PASO7 -> solicitar(PasoSagaPrincipal.PASO8); // punto de no retorno alcanzado
            case PASO8 -> completarYArrancarSagas();
        };
    }

    @Override
    protected EnumSet<PasoSagaPrincipal> pasosConDatosManualesObligatorios() {
        return REQUIEREN_DATOS_MANUALES;
    }

    /**
     * Al completar el PASO8 la saga muere y decide arrancar las 3 sagas
     * secundarias, cada una con su contexto recortado. Se crean en la MISMA
     * transacción; a partir de ahí son sagas independientes.
     */
    private List<Decision<PasoSagaPrincipal>> completarYArrancarSagas() {
        var decisiones = new ArrayList<Decision<PasoSagaPrincipal>>(List.of(
                new Decision.ArrancarSaga<>(new ContextoArranque.ArranqueSecundaria1(
                        externalId, ctx.refPaso1())),
                new Decision.ArrancarSaga<>(new ContextoArranque.ArranqueSecundaria2(
                        externalId, ctx.refPaso5())),
                new Decision.ArrancarSaga<>(new ContextoArranque.ArranqueSecundaria3(
                        externalId, ctx.refPaso7()))));
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
        return pasos.get(PasoSagaPrincipal.PASO7).estado().cuentaComoCompletado();
    }

    /** Cancela la saga. Solo posible ANTES de que PASO7 complete. Compensa PASO2 y PASO1. */
    public List<Decision<PasoSagaPrincipal>> cancelarPorSoporte(UsuarioSoporte quien, String motivo) {
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
        cancelarPasosActivos();

        var decisiones = new ArrayList<Decision<PasoSagaPrincipal>>();
        if (completado(PasoSagaPrincipal.PASO2)) {
            decisiones.add(new Decision.Compensar<>(PasoSagaPrincipal.PASO2,
                    new ComandoPasoPrincipal.CompensarPaso2(ctx.refPaso2())));
        }
        if (completado(PasoSagaPrincipal.PASO1)) {
            decisiones.add(new Decision.Compensar<>(PasoSagaPrincipal.PASO1,
                    new ComandoPasoPrincipal.CompensarPaso1(ctx.refPaso1())));
        }
        return List.copyOf(decisiones);
    }

    // ------------------------------------------------------------------
    // Resultado de las compensaciones (las ejecuta aplicación y reporta aquí)
    // ------------------------------------------------------------------

    public void compensacionCompletada(PasoSagaPrincipal paso) {
        marcarCompensado(paso);
    }

    public List<Decision<PasoSagaPrincipal>> compensacionFallida(PasoSagaPrincipal paso, MotivoFallo motivo) {
        // Una compensación que falla deja inconsistencia real: ticket inmediato.
        return List.of(new Decision.AbrirTicketSoporte<>(
                id, TipoSaga.PRINCIPAL, paso, motivo, pasoDe(paso).intentos(), false));
    }

    // ------------------------------------------------------------------
    // Helpers y lectura
    // ------------------------------------------------------------------

    private boolean completado(PasoSagaPrincipal p) {
        return pasos.get(p).estado().cuentaComoCompletado();
    }

    public ContextoTramitacion contexto() { return ctx; }
}
