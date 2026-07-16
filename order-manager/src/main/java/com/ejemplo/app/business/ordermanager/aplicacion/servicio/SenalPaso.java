package com.ejemplo.app.business.ordermanager.aplicacion.servicio;

import java.time.Duration;

import com.ejemplo.app.business.ordermanager.dominio.comun.ResultadoOrden;

/** Lo que deja un paso de ejecución. Ya persistido por el servicio de la saga; ServicioContinuarSaga solo la interpreta. */
public sealed interface SenalPaso {

    /** El agregado avanzó y se guardó: hay más trabajo listo para seguir en el mismo bucle. */
    record HayMasTrabajo() implements SenalPaso {}

    /** La orden queda a la espera de un evento externo. */
    record Aparcar(Duration ventana) implements SenalPaso {}

    /** La saga terminó. */
    record Finalizada(ResultadoOrden resultado) implements SenalPaso {}
}
