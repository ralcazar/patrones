package com.ejemplo.tramitacion.aplicacion.saga.servicio;

import com.ejemplo.tramitacion.aplicacion.saga.tarea.TareaSaga;
import com.ejemplo.tramitacion.dominio.saga.general.MensajeId;
import com.ejemplo.tramitacion.dominio.saga.general.MotivoFallo;
import com.ejemplo.tramitacion.dominio.saga.general.Paso;
import com.ejemplo.tramitacion.dominio.saga.general.ResultadoPaso;

/**
 * Router de tareas: el ProcesadorOrdenSaga del GestorOrdenes deserializa el
 * contenido de cada Orden y lo entrega aquí. "Procesar una orden" = ejecutar
 * exactamente uno de estos casos.
 *
 * Contrato de idempotencia del ProcesadorOrden: garantizado por los guards de
 * estado de los agregados (continuar/reintentar/completar son no-op si el
 * estado ya avanzó) y por la deduplicación de MensajeId para los resultados
 * externos del paso ASINCRONO.
 */
public class ManejadorTareasSaga {

    private final ServicioSagaPrincipal principal;
    private final ServicioSagasSucesoras sucesoras;

    public ManejadorTareasSaga(ServicioSagaPrincipal principal, ServicioSagasSucesoras sucesoras) {
        this.principal = principal;
        this.sucesoras = sucesoras;
    }

    public void procesar(TareaSaga tarea) {
        switch (tarea) {
            case TareaSaga.IniciarTramitacion t ->
                    principal.iniciarOContinuar(t.sagaId(), t.datoNegocio1Id(), t.datos(), t.datoNegocio2());

            case TareaSaga.ArrancarSaga t -> sucesoras.continuar(t.sagaId());

            case TareaSaga.Reintentar t -> {
                switch (t.tipo()) {
                    case PRINCIPAL -> principal.reintentar(t.sagaId(), t.paso());
                    case ASINCRONA, SECUENCIAL, SIMPLE -> sucesoras.reintentar(t.sagaId(), t.paso());
                }
            }

            case TareaSaga.TimeoutAsincrono t -> sucesoras.timeoutVencido(t.sagaId(), Paso.ASINCRONO);

            case TareaSaga.ResultadoAsincronoOk t -> sucesoras.pasoCompletado(t.sagaId(), Paso.ASINCRONO,
                    new ResultadoPaso.ResultadoAsincrono(t.ref()), MensajeId.externo(t.mensajeId()));

            case TareaSaga.ResultadoAsincronoError t -> sucesoras.pasoFallido(t.sagaId(), Paso.ASINCRONO,
                    new MotivoFallo(t.codigo(), t.detalle(), t.reintentable()), MensajeId.externo(t.mensajeId()));
        }
    }
}
