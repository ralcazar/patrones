package com.ejemplo.app.business.sagas.dominio.sagasecundaria2;

import java.util.List;
import java.util.Map;
import java.util.Objects;

import org.jmolecules.ddd.annotation.ValueObject;

import com.ejemplo.app.business.ordermanager.dominio.AuditoriaIntervencion;
import com.ejemplo.app.business.ordermanager.dominio.ComandoPaso;
import com.ejemplo.app.business.sagas.dominio.comun.ContextoArranque;
import com.ejemplo.app.business.ordermanager.dominio.ExternalId;
import com.ejemplo.app.business.ordermanager.dominio.PasoNoIntervenibleException;
import com.ejemplo.app.business.sagas.dominio.comun.RefPaso5;
import com.ejemplo.app.business.ordermanager.dominio.ResultadoPaso;
import com.ejemplo.app.business.ordermanager.dominio.OrdenId;
import com.ejemplo.app.business.ordermanager.dominio.Proceso;
import com.ejemplo.app.business.ordermanager.dominio.TipoOrden;
import com.ejemplo.app.business.ordermanager.dominio.UsuarioSoporte;

/**
 * Saga SECUNDARIA2: un único paso SOLICITUD. La solicitud es una llamada REST;
 * el servicio destino responde a posteriori publicando un evento Kafka que
 * puede tardar (ventana de 3h vigilada por la capa de aplicación). El
 * agregado no sabe nada de ese mecanismo: solo modela sus tres estados
 * (INICIAL -&gt; ESPERANDO_RESPUESTA -&gt; TERMINADA).
 *
 * Nace cuando la principal alcanza TERMINADA (después del punto de no
 * retorno): nunca se cancela ni compensa.
 *
 * Value object inmutable (ver {@link Proceso}): cada transición devuelve una
 * instancia nueva de {@code SagaSecundaria2}, dejando la original intacta.
 */
@ValueObject
public final class SagaSecundaria2 extends Proceso<EstadoSagaSecundaria2> {

    public static final TipoOrden TIPO = new TipoOrden("SECUNDARIA2");

    private final RefPaso5 refPaso5;
    private final RefRespuesta refRespuesta;

    private SagaSecundaria2(OrdenId id, ExternalId externalId, RefPaso5 refPaso5,
            EstadoSagaSecundaria2 estado) {
        this(id, externalId, refPaso5, null, estado, List.of());
    }

    private SagaSecundaria2(OrdenId id, ExternalId externalId, RefPaso5 refPaso5,
            RefRespuesta refRespuesta, EstadoSagaSecundaria2 estado, List<AuditoriaIntervencion> auditoria) {
        super(id, externalId, estado, auditoria);
        this.refPaso5 = refPaso5;
        this.refRespuesta = refRespuesta;
    }

    public static SagaSecundaria2 crear(OrdenId id, ContextoArranque.ArranqueSecundaria2 ctx) {
        return new SagaSecundaria2(id, ctx.externalId(), ctx.refPaso5(), EstadoSagaSecundaria2.INICIAL);
    }

    /** Para el adaptador de persistencia. */
    public static SagaSecundaria2 rehidratar(OrdenId id, ExternalId externalId, RefPaso5 refPaso5,
            RefRespuesta refRespuesta, EstadoSagaSecundaria2 estado, List<AuditoriaIntervencion> auditoria) {
        return new SagaSecundaria2(id, externalId, refPaso5, refRespuesta, estado, auditoria);
    }

    @Override public TipoOrden tipo() { return TIPO; }

    @Override
    public ComandoPaso comandoActual() {
        if (estado != EstadoSagaSecundaria2.INICIAL) {
            throw new IllegalStateException(
                    "La saga " + id.valor() + " no tiene paso pendiente en estado " + estado);
        }
        return new ComandoPasoSecundaria2.Solicitar(externalId, refPaso5);
    }

    @Override
    public SagaSecundaria2 aplicarYAvanzar(ResultadoPaso resultado) {
        if (!(resultado instanceof ResultadoPasoSecundaria2.Respuesta(var ref))) {
            throw new IllegalArgumentException("Resultado ajeno a la saga secundaria 2: " + resultado);
        }
        return respuestaRecibida(ref);
    }

    @Override
    public boolean terminada() {
        return estado == EstadoSagaSecundaria2.TERMINADA;
    }

    @Override
    public SagaSecundaria2 marcarPasoActualOkManual(UsuarioSoporte quien, String justificacion,
            Map<String, String> datos) {
        if (estado == EstadoSagaSecundaria2.TERMINADA) {
            throw new PasoNoIntervenibleException(id, "no tiene paso pendiente en estado " + estado);
        }
        var nuevaAuditoria = auditar(quien, "MARCAR_OK_MANUAL", "SOLICITUD: " + justificacion);
        return new SagaSecundaria2(id, externalId, refPaso5, refRespuesta, EstadoSagaSecundaria2.TERMINADA,
                nuevaAuditoria);
    }

    /** La solicitud REST se envió: queda a la espera del evento Kafka de respuesta. */
    public SagaSecundaria2 solicitudEnviada() {
        if (estado != EstadoSagaSecundaria2.INICIAL) {
            throw new IllegalStateException(
                    "solicitudEnviada() invocado en estado " + estado);
        }
        return new SagaSecundaria2(id, externalId, refPaso5, refRespuesta,
                EstadoSagaSecundaria2.ESPERANDO_RESPUESTA, auditoria);
    }

    /** Llega el evento Kafka (o la conciliación) con la respuesta: la saga termina. */
    public SagaSecundaria2 respuestaRecibida(RefRespuesta ref) {
        return new SagaSecundaria2(id, externalId, refPaso5, ref, EstadoSagaSecundaria2.TERMINADA, auditoria);
    }

    public RefPaso5 refPaso5() { return refPaso5; }
    public RefRespuesta refRespuesta() { return refRespuesta; }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!super.equals(o)) return false;
        SagaSecundaria2 that = (SagaSecundaria2) o;
        return Objects.equals(refPaso5, that.refPaso5)
                && Objects.equals(refRespuesta, that.refRespuesta);
    }

    @Override
    public int hashCode() {
        return Objects.hash(super.hashCode(), refPaso5, refRespuesta);
    }
}
