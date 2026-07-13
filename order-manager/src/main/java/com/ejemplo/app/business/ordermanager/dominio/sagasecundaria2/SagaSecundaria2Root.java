package com.ejemplo.app.business.ordermanager.dominio.sagasecundaria2;

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
import com.ejemplo.app.business.ordermanager.dominio.comun.RefPaso5;
import com.ejemplo.app.business.ordermanager.dominio.comun.ResultadoPaso;
import com.ejemplo.app.business.ordermanager.dominio.comun.Saga;
import com.ejemplo.app.business.ordermanager.dominio.comun.SagaId;
import com.ejemplo.app.business.ordermanager.dominio.comun.TipoSaga;

/**
 * Saga SECUNDARIA2: un único paso SOLICITUD. La solicitud es una llamada REST;
 * el servicio destino responde a posteriori publicando un evento Kafka que
 * puede tardar. El agregado no sabe nada de ese mecanismo: lo resuelven el
 * adaptador REST, el consumer de Kafka y el timeout de 24h que vigila la
 * capa de aplicación.
 *
 * Nace cuando la principal completa (después del punto de no retorno):
 * nunca se cancela ni compensa.
 */
@AggregateRoot
public final class SagaSecundaria2Root extends Saga<PasoSagaSecundaria2> {

    private final RefPaso5 refPaso5;
    private RefRespuesta refRespuesta;

    private SagaSecundaria2Root(SagaId id, ExternalId externalId, RefPaso5 refPaso5) {
        super(id, externalId, PasoSagaSecundaria2.class,
                EnumSet.allOf(PasoSagaSecundaria2.class), EstadoSaga.INICIADA, 0L);
        this.refPaso5 = refPaso5;
    }

    private SagaSecundaria2Root(SagaId id, ExternalId externalId, RefPaso5 refPaso5,
            RefRespuesta refRespuesta,
            EnumMap<PasoSagaSecundaria2, EjecucionPaso<PasoSagaSecundaria2>> pasos,
            List<AuditoriaIntervencion> auditoria, EstadoSaga estado, long version) {
        super(id, externalId, PasoSagaSecundaria2.class, pasos, auditoria, estado, version);
        this.refPaso5 = refPaso5;
        this.refRespuesta = refRespuesta;
    }

    public static SagaSecundaria2Root crear(SagaId id, ContextoArranque.ArranqueSecundaria2 ctx) {
        return new SagaSecundaria2Root(id, ctx.externalId(), ctx.refPaso5());
    }

    /** Para el adaptador de persistencia. */
    public static SagaSecundaria2Root rehidratar(SagaId id, ExternalId externalId,
            RefPaso5 refPaso5, RefRespuesta refRespuesta,
            EnumMap<PasoSagaSecundaria2, EjecucionPaso<PasoSagaSecundaria2>> pasos,
            List<AuditoriaIntervencion> auditoria, EstadoSaga estado, long version) {
        return new SagaSecundaria2Root(id, externalId, refPaso5, refRespuesta,
                pasos, auditoria, estado, version);
    }

    @Override public TipoSaga tipo() { return TipoSaga.SECUNDARIA2; }

    @Override protected PasoSagaSecundaria2 pasoInicial() { return PasoSagaSecundaria2.SOLICITUD; }

    @Override
    protected ComandoPaso comandoPara(PasoSagaSecundaria2 paso) {
        return new ComandoPasoSecundaria2.Solicitar(externalId, refPaso5);
    }

    @Override
    protected void aplicarResultado(PasoSagaSecundaria2 paso, ResultadoPaso resultado) {
        if (resultado instanceof ResultadoPasoSecundaria2.Respuesta(var ref)) {
            this.refRespuesta = ref;
        }
    }

    @Override
    protected List<Decision<PasoSagaSecundaria2>> transicionTras(PasoSagaSecundaria2 paso) {
        return finalizar();
    }

    public RefPaso5 refPaso5() { return refPaso5; }
    public RefRespuesta refRespuesta() { return refRespuesta; }
}
