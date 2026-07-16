package com.ejemplo.app.business.ordermanager.dominio.sagasecundaria1;

import java.util.List;
import java.util.Map;

import org.jmolecules.ddd.annotation.AggregateRoot;

import com.ejemplo.app.business.ordermanager.dominio.comun.AuditoriaIntervencion;
import com.ejemplo.app.business.ordermanager.dominio.comun.ComandoPaso;
import com.ejemplo.app.business.ordermanager.dominio.comun.ContextoArranque;
import com.ejemplo.app.business.ordermanager.dominio.comun.DatosManualesRequeridosException;
import com.ejemplo.app.business.ordermanager.dominio.comun.ExternalId;
import com.ejemplo.app.business.ordermanager.dominio.comun.PasoNoIntervenibleException;
import com.ejemplo.app.business.ordermanager.dominio.comun.RefPaso1;
import com.ejemplo.app.business.ordermanager.dominio.comun.ResultadoOrden;
import com.ejemplo.app.business.ordermanager.dominio.comun.ResultadoPaso;
import com.ejemplo.app.business.ordermanager.dominio.comun.SagaId;
import com.ejemplo.app.business.ordermanager.dominio.comun.SagaRoot;
import com.ejemplo.app.business.ordermanager.dominio.comun.TipoSaga;
import com.ejemplo.app.business.ordermanager.dominio.comun.UsuarioSoporte;

/**
 * Saga SECUNDARIA1: dos pasos síncronos encadenados, INICIO -> CONFIRMACION,
 * cada uno una llamada REST a un método distinto del mismo servicio.
 * CONFIRMACION consume la referencia que produce INICIO, por eso marcar-OK
 * manual de INICIO exige aportar esa referencia.
 *
 * Nace cuando la principal alcanza TERMINADA (después del punto de no
 * retorno): nunca se cancela ni compensa.
 */
@AggregateRoot
public final class SagaSecundaria1Root extends SagaRoot<EstadoSagaSecundaria1> {

    private final RefPaso1 refPaso1;
    private RefInicio refInicio;   // lo produce INICIO, lo consume CONFIRMACION
    private RefConfirmacion refConfirmacion;

    private SagaSecundaria1Root(SagaId id, ExternalId externalId, RefPaso1 refPaso1,
            EstadoSagaSecundaria1 estado) {
        super(id, externalId, estado);
        this.refPaso1 = refPaso1;
    }

    private SagaSecundaria1Root(SagaId id, ExternalId externalId, RefPaso1 refPaso1,
            RefInicio refInicio, RefConfirmacion refConfirmacion, EstadoSagaSecundaria1 estado,
            List<AuditoriaIntervencion> auditoria) {
        super(id, externalId, estado, auditoria);
        this.refPaso1 = refPaso1;
        this.refInicio = refInicio;
        this.refConfirmacion = refConfirmacion;
    }

    public static SagaSecundaria1Root crear(SagaId id, ContextoArranque.ArranqueSecundaria1 ctx) {
        return new SagaSecundaria1Root(id, ctx.externalId(), ctx.refPaso1(), EstadoSagaSecundaria1.INICIAL);
    }

    /** Para el adaptador de persistencia. */
    public static SagaSecundaria1Root rehidratar(SagaId id, ExternalId externalId, RefPaso1 refPaso1,
            RefInicio refInicio, RefConfirmacion refConfirmacion, EstadoSagaSecundaria1 estado,
            List<AuditoriaIntervencion> auditoria) {
        return new SagaSecundaria1Root(id, externalId, refPaso1, refInicio, refConfirmacion,
                estado, auditoria);
    }

    @Override public TipoSaga tipo() { return TipoSaga.SECUNDARIA1; }

    @Override
    public ComandoPaso comandoActual() {
        return switch (estado) {
            case INICIAL -> new ComandoPasoSecundaria1.Iniciar(externalId, refPaso1);
            case INICIO_HECHO -> new ComandoPasoSecundaria1.Confirmar(refInicio);
            default -> throw new IllegalStateException(
                    "La saga " + id.valor() + " no tiene paso pendiente en estado " + estado);
        };
    }

    @Override
    public void aplicarYAvanzar(ResultadoPaso resultado) {
        if (!(resultado instanceof ResultadoPasoSecundaria1 r)) {
            throw new IllegalArgumentException("Resultado ajeno a la saga secundaria 1: " + resultado);
        }
        aplicarDatos(r);
        avanzar();
    }

    @Override
    public boolean terminada() {
        return estado == EstadoSagaSecundaria1.TERMINADA;
    }

    @Override
    public ResultadoOrden resultadoFinal() {
        return ResultadoOrden.FINALIZADA_OK;
    }

    @Override
    public void marcarPasoActualOkManual(UsuarioSoporte quien, String justificacion, Map<String, String> datos) {
        if (estado == EstadoSagaSecundaria1.TERMINADA) {
            throw new PasoNoIntervenibleException(id, "no tiene paso pendiente en estado " + estado);
        }
        if (estado == EstadoSagaSecundaria1.INICIAL && (datos == null || datos.isEmpty())) {
            throw new DatosManualesRequeridosException(id, estado.name());
        }
        var resultado = construirResultadoManual(datos);
        if (resultado != null) {
            aplicarDatos(resultado);
        }
        var estadoAnterior = estado;
        avanzar();
        auditar(quien, "MARCAR_OK_MANUAL", estadoAnterior + ": " + justificacion);
    }

    private void aplicarDatos(ResultadoPasoSecundaria1 r) {
        switch (r) {
            case ResultadoPasoSecundaria1.Iniciada(var ref) -> this.refInicio = ref;
            case ResultadoPasoSecundaria1.Confirmada(var ref) -> this.refConfirmacion = ref;
        }
    }

    private void avanzar() {
        estado = switch (estado) {
            case INICIAL -> EstadoSagaSecundaria1.INICIO_HECHO;
            case INICIO_HECHO -> EstadoSagaSecundaria1.TERMINADA;
            default -> throw new IllegalStateException(
                    "La saga " + id.valor() + " no tiene paso pendiente en estado " + estado);
        };
    }

    /** Solo INICIO exige datos: CONFIRMACION cierra la saga y nadie consume su resultado. */
    private ResultadoPasoSecundaria1 construirResultadoManual(Map<String, String> datos) {
        if (datos == null || datos.isEmpty() || estado != EstadoSagaSecundaria1.INICIAL) {
            return null;
        }
        return new ResultadoPasoSecundaria1.Iniciada(new RefInicio(requerido(datos, "refInicio")));
    }

    private static String requerido(Map<String, String> datos, String clave) {
        var valor = datos.get(clave);
        if (valor == null || valor.isBlank()) {
            throw new IllegalArgumentException("Falta el dato manual obligatorio: " + clave);
        }
        return valor;
    }

    public RefPaso1 refPaso1() { return refPaso1; }
    public RefInicio refInicio() { return refInicio; }
    public RefConfirmacion refConfirmacion() { return refConfirmacion; }
}
