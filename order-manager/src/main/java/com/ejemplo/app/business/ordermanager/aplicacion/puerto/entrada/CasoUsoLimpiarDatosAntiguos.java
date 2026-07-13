package com.ejemplo.app.business.ordermanager.aplicacion.puerto.entrada;

import java.time.Instant;

/**
 * Limpieza periódica de datos ya no utilizados: sagas que acabaron BIEN
 * (COMPLETADA/CANCELADA) y son antiguas, sus tareas ya procesadas y los
 * registros de deduplicación viejos. Lo invoca un planificador de
 * infraestructura cada cierto tiempo.
 *
 * Nunca borra trabajo vivo: sagas EN_CURSO, pasos bloqueados esperando a
 * soporte o tareas pendientes/diferidas quedan siempre fuera del corte.
 */
public interface CasoUsoLimpiarDatosAntiguos {

    /** Purga todo lo finalizado-bien anterior al corte y devuelve el recuento borrado. */
    ResultadoLimpieza purgarAnterioresA(Instant corte);

    record ResultadoLimpieza(long sagasPrincipales, long sagasSecundarias,
                             long mensajesDedup, long tareasTerminadas) {
        public long total() {
            return sagasPrincipales + sagasSecundarias + mensajesDedup + tareasTerminadas;
        }
    }
}
