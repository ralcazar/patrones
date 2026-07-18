package com.ejemplo.app.business.sagas.dominio.sagaprincipal;

import java.util.List;
import java.util.Map;

import org.jmolecules.ddd.annotation.Entity;

import com.ejemplo.app.business.ordermanager.dominio.AuditoriaIntervencion;
import com.ejemplo.app.business.ordermanager.dominio.ComandoPaso;
import com.ejemplo.app.business.sagas.dominio.comun.ContextoArranque;
import com.ejemplo.app.business.ordermanager.dominio.DatosManualesRequeridosException;
import com.ejemplo.app.business.ordermanager.dominio.ExternalId;
import com.ejemplo.app.business.ordermanager.dominio.PasoNoIntervenibleException;
import com.ejemplo.app.business.sagas.dominio.sagaprincipal.PuntoNoRetornoSuperadoException;
import com.ejemplo.app.business.sagas.dominio.comun.RefPaso1;
import com.ejemplo.app.business.sagas.dominio.comun.RefPaso5;
import com.ejemplo.app.business.sagas.dominio.comun.RefPaso7;
import com.ejemplo.app.business.ordermanager.dominio.ResultadoPaso;
import com.ejemplo.app.business.ordermanager.dominio.OrdenId;
import com.ejemplo.app.business.ordermanager.dominio.Proceso;
import com.ejemplo.app.business.ordermanager.dominio.OrdenYaCompletadaException;
import com.ejemplo.app.business.ordermanager.dominio.TipoOrden;
import com.ejemplo.app.business.ordermanager.dominio.UsuarioSoporte;

/**
 * Saga principal: PASO1 -> PASO2 -> ... -> PASO8, todos síncronos.
 *
 * Reglas de negocio que este agregado encapsula:
 * - Punto de no retorno: una vez alcanzado PASO7_HECHO, la saga ya no es
 *   cancelable.
 * - Cancelación (solo antes del punto de no retorno): compensa PASO2 y PASO1,
 *   en orden inverso, vía los estados COMPENSAR_PASO2 -&gt; COMPENSAR_PASO1 -&gt;
 *   CANCELADA. La cancelación queda reflejada en el estado y la auditoría; no
 *   se publica ningún evento.
 * - Al alcanzar TERMINADA (tras PASO8): la saga decide arrancar las 3 sagas
 *   secundarias (SECUNDARIA1, SECUNDARIA2, SECUNDARIA3), independientes entre
 *   sí y sin join.
 */
@Entity
public final class SagaPrincipal extends Proceso<EstadoSagaPrincipal> {

    public static final TipoOrden TIPO = new TipoOrden("PRINCIPAL");

    private ContextoTramitacion ctx;

    private SagaPrincipal(OrdenId id, ExternalId externalId, ContextoTramitacion ctx,
                              EstadoSagaPrincipal estado) {
        super(id, externalId, estado);
        this.ctx = ctx;
    }

    private SagaPrincipal(OrdenId id, ExternalId externalId, ContextoTramitacion ctx,
                              EstadoSagaPrincipal estado, List<AuditoriaIntervencion> auditoria) {
        super(id, externalId, estado, auditoria);
        this.ctx = ctx;
    }

    public static SagaPrincipal crear(OrdenId id, ExternalId externalId,
                                          DatoNegocio3 datos, DatoNegocio2 datoNegocio2) {
        return new SagaPrincipal(id, externalId, ContextoTramitacion.inicial(datos, datoNegocio2),
                EstadoSagaPrincipal.INICIAL);
    }

    /** Para el adaptador de persistencia. */
    public static SagaPrincipal rehidratar(OrdenId id, ExternalId externalId, ContextoTramitacion ctx,
            EstadoSagaPrincipal estado, List<AuditoriaIntervencion> auditoria) {
        return new SagaPrincipal(id, externalId, ctx, estado, auditoria);
    }

    // ------------------------------------------------------------------
    // Especialización del ciclo de vida común de Proceso
    // ------------------------------------------------------------------

    @Override public TipoOrden tipo() { return TIPO; }

    @Override
    public ComandoPaso comandoActual() {
        return switch (estado) {
            case INICIAL -> new ComandoPasoPrincipal.EjecutarPaso1(externalId, ctx.datoNegocio3());
            case PASO1_HECHO -> new ComandoPasoPrincipal.EjecutarPaso2(ctx.refPaso1());
            case PASO2_HECHO -> new ComandoPasoPrincipal.EjecutarPaso3(externalId, ctx.refPaso2());
            case PASO3_HECHO -> new ComandoPasoPrincipal.EjecutarPaso4(ctx.refPaso1(), ctx.refPaso2());
            case PASO4_HECHO -> new ComandoPasoPrincipal.EjecutarPaso5(ctx.refPaso4());
            case PASO5_HECHO -> new ComandoPasoPrincipal.EjecutarPaso6(ctx.refPaso5());
            case PASO6_HECHO -> new ComandoPasoPrincipal.EjecutarPaso7(ctx.refPaso5(), ctx.datoNegocio2());
            case PASO7_HECHO -> new ComandoPasoPrincipal.EjecutarPaso8(externalId, ctx.refPaso7());
            default -> throw new IllegalStateException(
                    "La saga " + id.valor() + " no tiene paso pendiente en estado " + estado);
        };
    }

    @Override
    public void aplicarYAvanzar(ResultadoPaso resultado) {
        if (!(resultado instanceof ResultadoPasoPrincipal r)) {
            throw new IllegalArgumentException("Resultado ajeno a la saga principal: " + resultado);
        }
        ctx = ctx.aplicar(r);
        avanzar();
    }

    @Override
    public boolean terminada() {
        return estado == EstadoSagaPrincipal.TERMINADA || estado == EstadoSagaPrincipal.CANCELADA;
    }

    @Override
    public void marcarPasoActualOkManual(UsuarioSoporte quien, String justificacion, Map<String, String> datos) {
        if (!tienePasoPendiente(estado)) {
            throw new PasoNoIntervenibleException(id, "no tiene paso pendiente en estado " + estado);
        }
        if (requiereDatosManuales(estado) && (datos == null || datos.isEmpty())) {
            throw new DatosManualesRequeridosException(id, estado.name());
        }
        var resultado = construirResultadoManual(estado, datos);
        if (resultado != null) {
            ctx = ctx.aplicar(resultado);
        }
        var estadoAnterior = estado;
        avanzar();
        auditar(quien, "MARCAR_OK_MANUAL", estadoAnterior + ": " + justificacion);
    }

    /** Al alcanzar TERMINADA (tras PASO8), la saga arranca las 3 secundarias con su contexto recortado. */
    public List<ContextoArranque> contextosArranque() {
        return List.of(
                new ContextoArranque.ArranqueSecundaria1(externalId, ctx.refPaso1()),
                new ContextoArranque.ArranqueSecundaria2(externalId, ctx.refPaso5()),
                new ContextoArranque.ArranqueSecundaria3(externalId, ctx.refPaso7()));
    }

    private void avanzar() {
        estado = switch (estado) {
            case INICIAL -> EstadoSagaPrincipal.PASO1_HECHO;
            case PASO1_HECHO -> EstadoSagaPrincipal.PASO2_HECHO;
            case PASO2_HECHO -> EstadoSagaPrincipal.PASO3_HECHO;
            case PASO3_HECHO -> EstadoSagaPrincipal.PASO4_HECHO;
            case PASO4_HECHO -> EstadoSagaPrincipal.PASO5_HECHO;
            case PASO5_HECHO -> EstadoSagaPrincipal.PASO6_HECHO;
            case PASO6_HECHO -> EstadoSagaPrincipal.PASO7_HECHO;
            case PASO7_HECHO -> EstadoSagaPrincipal.TERMINADA;
            default -> throw new IllegalStateException(
                    "La saga " + id.valor() + " no tiene paso pendiente en estado " + estado);
        };
    }

    private static boolean tienePasoPendiente(EstadoSagaPrincipal estado) {
        return estado.ordinal() < EstadoSagaPrincipal.TERMINADA.ordinal();
    }

    /** Pasos cuyo resultado consumen pasos posteriores u otras sagas: marcar-OK manual exige aportarlo. */
    private static boolean requiereDatosManuales(EstadoSagaPrincipal estado) {
        return switch (estado) {
            case INICIAL, PASO1_HECHO, PASO3_HECHO, PASO4_HECHO, PASO6_HECHO -> true;
            default -> false;
        };
    }

    private static ResultadoPasoPrincipal construirResultadoManual(EstadoSagaPrincipal estado,
            Map<String, String> datos) {
        if (datos == null || datos.isEmpty()) {
            return null;
        }
        return switch (estado) {
            case INICIAL -> new ResultadoPasoPrincipal.ResultadoPaso1(new RefPaso1(requerido(datos, "refPaso1")));
            case PASO1_HECHO -> new ResultadoPasoPrincipal.ResultadoPaso2(new RefPaso2(requerido(datos, "refPaso2")));
            case PASO3_HECHO -> new ResultadoPasoPrincipal.ResultadoPaso4(new RefPaso4(requerido(datos, "refPaso4")));
            case PASO4_HECHO -> new ResultadoPasoPrincipal.ResultadoPaso5(new RefPaso5(requerido(datos, "refPaso5")));
            case PASO6_HECHO -> new ResultadoPasoPrincipal.ResultadoPaso7(new RefPaso7(requerido(datos, "refPaso7")));
            default -> null;
        };
    }

    private static String requerido(Map<String, String> datos, String clave) {
        var valor = datos.get(clave);
        if (valor == null || valor.isBlank()) {
            throw new IllegalArgumentException("Falta el dato manual obligatorio: " + clave);
        }
        return valor;
    }

    // ------------------------------------------------------------------
    // Cancelación y compensación, exclusivas de la principal
    // ------------------------------------------------------------------

    public boolean esCancelable() {
        return estado.ordinal() < EstadoSagaPrincipal.PASO7_HECHO.ordinal();
    }

    /** Cancela la saga. Solo posible ANTES de alcanzar PASO7_HECHO. Dispara la compensación de PASO2 y PASO1. */
    public void cancelar(UsuarioSoporte quien, String motivo) {
        if (estado == EstadoSagaPrincipal.TERMINADA) {
            throw new OrdenYaCompletadaException(id);
        }
        if (estado == EstadoSagaPrincipal.COMPENSAR_PASO2
                || estado == EstadoSagaPrincipal.COMPENSAR_PASO1
                || estado == EstadoSagaPrincipal.CANCELADA) {
            return; // idempotente
        }
        if (estado == EstadoSagaPrincipal.PASO7_HECHO) {
            throw new PuntoNoRetornoSuperadoException(id);
        }

        estado = estado.ordinal() >= EstadoSagaPrincipal.PASO2_HECHO.ordinal()
                ? EstadoSagaPrincipal.COMPENSAR_PASO2
                : estado.ordinal() >= EstadoSagaPrincipal.PASO1_HECHO.ordinal()
                        ? EstadoSagaPrincipal.COMPENSAR_PASO1
                        : EstadoSagaPrincipal.CANCELADA;
        auditar(quien, "CANCELAR", motivo);
    }

    /** El servicio de la saga ya ejecutó la compensación del paso correspondiente al estado actual. */
    public void compensacionCompletada() {
        estado = switch (estado) {
            case COMPENSAR_PASO2 -> EstadoSagaPrincipal.COMPENSAR_PASO1;
            case COMPENSAR_PASO1 -> EstadoSagaPrincipal.CANCELADA;
            default -> throw new IllegalStateException(
                    "compensacionCompletada() invocado en estado " + estado);
        };
    }

    public ContextoTramitacion contexto() { return ctx; }
}
