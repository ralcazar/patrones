package com.ejemplo.app.business.ordermanager.aplicacion.servicio;

import org.jmolecules.ddd.annotation.Service;

import com.ejemplo.app.business.ordermanager.aplicacion.tarea.TareaSaga;
import com.ejemplo.app.business.ordermanager.dominio.comun.MensajeId;
import com.ejemplo.app.business.ordermanager.dominio.comun.MotivoFallo;
import com.ejemplo.app.business.ordermanager.dominio.sagaprincipal.PasoSagaPrincipal;
import com.ejemplo.app.business.ordermanager.dominio.sagasecundaria1.PasoSagaSecundaria1;
import com.ejemplo.app.business.ordermanager.dominio.sagasecundaria2.PasoSagaSecundaria2;
import com.ejemplo.app.business.ordermanager.dominio.sagasecundaria2.ResultadoPasoSecundaria2;
import com.ejemplo.app.business.ordermanager.dominio.sagasecundaria3.PasoSagaSecundaria3;

/**
 * Router de tareas: el adaptador de recepción decodifica el contenido de cada
 * Orden a TareaSaga y el ServicioDespachoTareas la entrega aquí. "Procesar una
 * orden" = ejecutar exactamente uno de estos casos, ruteado al orquestador de
 * la saga correcta.
 *
 * Esta es la única frontera donde el PasoSaga genérico se baja al enum
 * concreto (cast por rama del switch sobre TipoSaga).
 *
 * Contrato de idempotencia del despacho: garantizado por los guards de
 * estado de los agregados (continuar/reintentar/completar son no-op si el
 * estado ya avanzó) y por la deduplicación de MensajeId para los resultados
 * externos de la saga secundaria 2.
 */
@Service
public class ManejadorTareasSaga {

    private final ServicioSagaPrincipal principal;
    private final ServicioSagaSecundaria1 secundaria1;
    private final ServicioSagaSecundaria2 secundaria2;
    private final ServicioSagaSecundaria3 secundaria3;

    public ManejadorTareasSaga(ServicioSagaPrincipal principal, ServicioSagaSecundaria1 secundaria1,
                               ServicioSagaSecundaria2 secundaria2, ServicioSagaSecundaria3 secundaria3) {
        this.principal = principal;
        this.secundaria1 = secundaria1;
        this.secundaria2 = secundaria2;
        this.secundaria3 = secundaria3;
    }

    public void procesar(TareaSaga tarea) {
        switch (tarea) {
            case TareaSaga.IniciarTramitacion t ->
                    principal.iniciarOContinuar(t.sagaId(), t.externalId(), t.datos(), t.datoNegocio2());

            case TareaSaga.ArrancarSaga t -> {
                switch (t.tipo()) {
                    case PRINCIPAL -> throw new IllegalArgumentException(
                            "La saga principal no se arranca con ArrancarSaga: " + t);
                    case SECUNDARIA1 -> secundaria1.continuar(t.sagaId());
                    case SECUNDARIA2 -> secundaria2.continuar(t.sagaId());
                    case SECUNDARIA3 -> secundaria3.continuar(t.sagaId());
                }
            }

            case TareaSaga.Reintentar t -> {
                switch (t.tipo()) {
                    case PRINCIPAL -> principal.reintentar(t.sagaId(), (PasoSagaPrincipal) t.paso());
                    case SECUNDARIA1 -> secundaria1.reintentar(t.sagaId(), (PasoSagaSecundaria1) t.paso());
                    case SECUNDARIA2 -> secundaria2.reintentar(t.sagaId(), (PasoSagaSecundaria2) t.paso());
                    case SECUNDARIA3 -> secundaria3.reintentar(t.sagaId(), (PasoSagaSecundaria3) t.paso());
                }
            }

            case TareaSaga.TimeoutSagaSecundaria2 t -> secundaria2.timeoutVencido(t.sagaId());

            case TareaSaga.ResultadoSagaSecundaria2Ok t -> secundaria2.pasoCompletado(
                    t.sagaId(), PasoSagaSecundaria2.SOLICITUD,
                    new ResultadoPasoSecundaria2.Respuesta(t.ref()), MensajeId.externo(t.mensajeId()));

            case TareaSaga.ResultadoSagaSecundaria2Error t -> secundaria2.pasoFallido(
                    t.sagaId(), PasoSagaSecundaria2.SOLICITUD,
                    new MotivoFallo(t.codigo(), t.detalle(), t.reintentable()), MensajeId.externo(t.mensajeId()));
        }
    }
}
