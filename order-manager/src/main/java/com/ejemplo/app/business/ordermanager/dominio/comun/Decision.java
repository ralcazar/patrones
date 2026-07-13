package com.ejemplo.app.business.ordermanager.dominio.comun;

import java.time.Duration;

/**
 * El output de los agregados. El dominio DECIDE; la capa de aplicación
 * despacha estas decisiones contra los puertos de salida.
 *
 * Genérica en el tipo de paso de la saga que decide: los switches de la capa
 * de aplicación sobre este sealed son exhaustivos.
 */
public sealed interface Decision<P extends Enum<P> & PasoSaga> {

    record Ejecutar<P extends Enum<P> & PasoSaga>(P paso, ComandoPaso comando)
            implements Decision<P> {}

    record ProgramarReintento<P extends Enum<P> & PasoSaga>(P paso, Duration espera, int intentoNum)
            implements Decision<P> {}

    record AbrirTicketSoporte<P extends Enum<P> & PasoSaga>(
            SagaId sagaId, TipoSaga tipo, P paso, MotivoFallo motivo,
            int intentos, boolean sagaCancelable) implements Decision<P> {}

    record Compensar<P extends Enum<P> & PasoSaga>(P paso, ComandoPaso comando)
            implements Decision<P> {}

    /**
     * Emitida por la saga principal al completar el PASO8: arranca otra saga.
     * No crea ninguna relación padre-hija: la nueva saga nace independiente,
     * en la MISMA transacción que completa la que la arranca.
     */
    record ArrancarSaga<P extends Enum<P> & PasoSaga>(ContextoArranque contexto)
            implements Decision<P> {}
}
