package com.ejemplo.app.business.ordermanager.aplicacion.tarea;

/**
 * Una tarea ya reclamada de la cola, lista para procesarse: la {@link TareaSaga}
 * decodificada más el asa técnica que necesita el adaptador de salida para
 * cerrarla ({@code referencia} de la orden y {@code lease} del reclamo).
 *
 * Ambos campos son OPACOS para la aplicación: no los interpreta, solo los
 * devuelve al puerto al finalizar. Así el flujo de despacho pasa por la capa de
 * aplicación sin que esta conozca la Orden ni la tabla que la respalda.
 */
public record TareaReclamada(String referencia, String lease, TareaSaga tarea) {}
