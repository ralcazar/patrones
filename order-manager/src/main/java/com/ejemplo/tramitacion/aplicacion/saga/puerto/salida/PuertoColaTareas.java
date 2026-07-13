package com.ejemplo.tramitacion.aplicacion.saga.puerto.salida;

import java.time.Instant;

import com.ejemplo.tramitacion.aplicacion.saga.tarea.TareaSaga;

/**
 * Cola de tareas respaldada por la tabla ordenes del GestorOrdenes.
 *
 * CONTRATO CRÍTICO: encolar() debe UNIRSE a la transacción en curso
 * (propagation REQUIRED). Así el cambio de estado de la saga y la tarea
 * que lo continúa se commitean atómicamente. Esto sustituye al scheduler
 * persistente y al proceso de recuperación del diseño anterior.
 */
public interface PuertoColaTareas {

    void encolar(TareaSaga tarea);

    /** Con noAntesDe en el futuro: es como el GestorOrdenes implementa el backoff. */
    void encolar(TareaSaga tarea, Instant noAntesDe);

    /**
     * Limpieza de datos: borra las tareas ya COMPLETADAs anteriores al corte.
     * Las FALLIDAs se conservan: son señal de bug y las revisa una persona.
     * Devuelve cuántas borró.
     */
    long purgarTerminadasAntesDe(Instant corte);
}
