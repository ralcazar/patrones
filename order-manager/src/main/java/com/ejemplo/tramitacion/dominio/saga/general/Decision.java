package com.ejemplo.tramitacion.dominio.saga.general;

import java.time.Duration;

/**
 * El output de los agregados. El dominio DECIDE; la capa de aplicación
 * despacha estas decisiones contra los puertos de salida.
 */
public sealed interface Decision {

    record Ejecutar(Paso paso, ComandoPaso comando) implements Decision {}

    record ProgramarReintento(Paso paso, Duration espera, int intentoNum) implements Decision {}

    record AbrirTicketSoporte(SagaId sagaId, TipoSaga tipo, Paso paso, MotivoFallo motivo,
                              int intentos, boolean sagaCancelable) implements Decision {}

    record Compensar(Paso paso, ComandoPaso comando) implements Decision {}

    /**
     * Emitida por la saga principal al completar el PASO8: arranca otra saga.
     * No crea ninguna relación padre-hija: la nueva saga nace independiente,
     * en la MISMA transacción que completa la que la arranca.
     */
    record ArrancarSaga(ContextoArranque contexto) implements Decision {}

    record PublicarEvento(EventoTramitacion evento) implements Decision {}
}
