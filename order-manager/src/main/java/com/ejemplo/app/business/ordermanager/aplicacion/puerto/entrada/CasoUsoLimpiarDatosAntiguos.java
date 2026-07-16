package com.ejemplo.app.business.ordermanager.aplicacion.puerto.entrada;

import java.time.Instant;

/**
 * Limpieza periódica de datos ya no utilizados: órdenes que acabaron bien
 * (FINALIZADA_OK/FINALIZADA_COMPENSADA) y son antiguas, y los registros de
 * deduplicación de mensajes ya viejos. Lo invoca un planificador de
 * infraestructura cada cierto tiempo.
 *
 * Nunca borra trabajo vivo: cualquier orden sin resultado (viva, sea cual sea
 * su estado o sus intentos) sobrevive siempre a la limpieza.
 */
public interface CasoUsoLimpiarDatosAntiguos {

    /** Purga todo lo finalizado-bien anterior al corte y devuelve el recuento borrado. */
    ResultadoLimpieza purgarAnterioresA(Instant corte);

    record ResultadoLimpieza(long ordenes, long mensajesDedup) {
        public long total() {
            return ordenes + mensajesDedup;
        }
    }
}
