package com.ejemplo.app.business.ordermanager.aplicacion.puerto.salida;

/**
 * Puerto genérico de apertura de incidencias operativas: cuando una tarea de
 * fondo (p. ej. una purga por tramitación) agota sus reintentos, el servicio
 * de aplicación llama a {@link #abrir} para que una herramienta externa (log
 * estructurado, sistema de tickets, etc.) recoja el fallo. Vocabulario
 * neutro del motor: no conoce qué tarea concreta lo usa ni de qué dominio es.
 */
public interface PuertoIncidencias {

    /**
     * @param tarea nombre de la tarea que agotó reintentos (p. ej. "purga-adjuntos")
     * @param causa descripción del último error que provocó el agotamiento
     * @param intentos número de intentos realizados antes de abrir la incidencia
     */
    void abrir(String tarea, String causa, int intentos);
}
