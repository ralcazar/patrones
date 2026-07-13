package com.ejemplo.app.business.ordermanager.aplicacion.puerto.salida;

import java.time.Instant;

import com.ejemplo.app.business.ordermanager.dominio.comun.MensajeId;

/**
 * Deduplicación de mensajes externos (la mensajería es at-least-once).
 * registrar() se invoca dentro de la misma transacción que muta la saga.
 */
public interface PuertoMensajesProcesados {
    boolean yaProcesado(MensajeId msgId);
    void registrar(MensajeId msgId);
    /**
     * Limpieza de datos: borra los registros de deduplicación anteriores al
     * corte (ya no puede llegar un duplicado tan viejo). Devuelve cuántos borró.
     */
    long purgarAnterioresA(Instant corte);
}
