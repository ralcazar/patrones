package com.ejemplo.tramitacion.aplicacion.saga.servicio;

import com.ejemplo.tramitacion.aplicacion.saga.puerto.entrada.CasoUsoIniciarTramitacion;
import com.ejemplo.tramitacion.aplicacion.saga.puerto.salida.PuertoColaTareas;
import com.ejemplo.tramitacion.aplicacion.saga.tarea.TareaSaga;
import com.ejemplo.tramitacion.dominio.saga.general.SagaId;

/**
 * Iniciar una tramitación = encolar una Orden (intake durable): la petición
 * REST responde al instante con el sagaId y el pool del GestorOrdenes procesa
 * la saga. El sagaId se genera AQUÍ y viaja en la tarea: así la reentrega de
 * la misma tarea no crea sagas duplicadas (iniciarOContinuar es idempotente).
 */
public class ServicioEncolarTramitacion implements CasoUsoIniciarTramitacion {

    private final PuertoColaTareas cola;

    public ServicioEncolarTramitacion(PuertoColaTareas cola) {
        this.cola = cola;
    }

    @Override
    public SagaId iniciar(ComandoIniciarTramitacion cmd) {
        var sagaId = SagaId.nuevo();
        cola.encolar(new TareaSaga.IniciarTramitacion(
                sagaId, cmd.datoNegocio1Id(), cmd.datoNegocio3(), cmd.datoNegocio2()));
        return sagaId;
    }
}
