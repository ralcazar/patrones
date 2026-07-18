package com.ejemplo.app.business.sagas.dominio.sagasecundaria2;

import java.util.List;
import java.util.Map;

import org.jmolecules.ddd.annotation.Entity;

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
 * (INICIAL -&gt; ESPERANDO_RESPUESTA -&gt; TERMINADA) y, si la conciliación
 * detecta un fallo registrado, permite volver a solicitar.
 *
 * Nace cuando la principal alcanza TERMINADA (después del punto de no
 * retorno): nunca se cancela ni compensa.
 */
@Entity
public final class SagaSecundaria2 extends Proceso<EstadoSagaSecundaria2> {

    public static final TipoOrden TIPO = new TipoOrden("SECUNDARIA2");

    private final RefPaso5 refPaso5;
    private RefRespuesta refRespuesta;

    private SagaSecundaria2(OrdenId id, ExternalId externalId, RefPaso5 refPaso5,
            EstadoSagaSecundaria2 estado) {
        super(id, externalId, estado);
        this.refPaso5 = refPaso5;
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
    public void aplicarYAvanzar(ResultadoPaso resultado) {
        if (!(resultado instanceof ResultadoPasoSecundaria2.Respuesta(var ref))) {
            throw new IllegalArgumentException("Resultado ajeno a la saga secundaria 2: " + resultado);
        }
        respuestaRecibida(ref);
    }

    @Override
    public boolean terminada() {
        return estado == EstadoSagaSecundaria2.TERMINADA;
    }

    @Override
    public void marcarPasoActualOkManual(UsuarioSoporte quien, String justificacion, Map<String, String> datos) {
        if (estado == EstadoSagaSecundaria2.TERMINADA) {
            throw new PasoNoIntervenibleException(id, "no tiene paso pendiente en estado " + estado);
        }
        estado = EstadoSagaSecundaria2.TERMINADA;
        auditar(quien, "MARCAR_OK_MANUAL", "SOLICITUD: " + justificacion);
    }

    /** La solicitud REST se envió: queda a la espera del evento Kafka de respuesta. */
    public void solicitudEnviada() {
        if (estado != EstadoSagaSecundaria2.INICIAL) {
            throw new IllegalStateException(
                    "solicitudEnviada() invocado en estado " + estado);
        }
        estado = EstadoSagaSecundaria2.ESPERANDO_RESPUESTA;
    }

    /** Llega el evento Kafka (o la conciliación) con la respuesta: la saga termina. */
    public void respuestaRecibida(RefRespuesta ref) {
        this.refRespuesta = ref;
        estado = EstadoSagaSecundaria2.TERMINADA;
    }

    /** La conciliación detectó un fallo registrado en destino: hay que reintentar la solicitud. */
    public void volverASolicitar() {
        estado = EstadoSagaSecundaria2.INICIAL;
    }

    public RefPaso5 refPaso5() { return refPaso5; }
    public RefRespuesta refRespuesta() { return refRespuesta; }
}
