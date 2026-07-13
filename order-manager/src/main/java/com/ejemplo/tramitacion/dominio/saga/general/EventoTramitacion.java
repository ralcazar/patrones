package com.ejemplo.tramitacion.dominio.saga.general;

public sealed interface EventoTramitacion {
    /** Lo emite cualquier saga al completarse, sea del tipo que sea. */
    record SagaCompletada(SagaId sagaId, TipoSaga tipo, DatoNegocio1Id datoNegocio1Id)
            implements EventoTramitacion {}

    record TramitacionCancelada(SagaId sagaId, String motivo) implements EventoTramitacion {}
}
