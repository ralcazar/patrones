package com.ejemplo.app.business.ordermanager.aplicacion.servicio;

import java.time.Duration;

/** Lo que deja un paso de ejecución. Ya persistido por el procesador de la orden; ServicioContinuarOrden solo la interpreta. */
public sealed interface SenalPaso {

    /** El agregado avanzó y se guardó: hay más trabajo listo para seguir en el mismo bucle. */
    record HayMasTrabajo() implements SenalPaso {}

    /** La orden queda a la espera de un evento externo. */
    record Aparcar(Duration ventana) implements SenalPaso {}

    /** La orden terminó. */
    record Finalizada() implements SenalPaso {}
}
