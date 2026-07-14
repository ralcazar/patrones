package com.ejemplo.app.business.ordermanager.aplicacion.puerto.salida;

import java.util.Optional;

import com.ejemplo.app.business.ordermanager.aplicacion.tarea.TareaReclamada;

/**
 * Lado de RECEPCIÓN de la cola de tareas (el gemelo de {@link PuertoColaTareas},
 * que es el lado de ENVÍO). Lo implementa el adaptador de la tabla ordenes; lo
 * consume el servicio de aplicación de despacho, nunca un adaptador de entrada
 * directamente (regla de arquitectura del CLAUDE.md).
 *
 * El patrón lease vive detrás de este puerto: reclamar marca la orden EN_PROCESO
 * con un lease; finalizar la cierra (COMPLETADA/FALLIDA) solo si el lease sigue
 * siendo el nuestro. Si el proceso cae entre medias, el lease caduca y otro
 * trabajador la retoma.
 */
public interface PuertoRecepcionTareas {

    /** ¿Hay al menos una orden elegible (PENDIENTE o con lease caducado, no diferida)? */
    boolean hayPendientes();

    /**
     * Reclama la siguiente orden elegible bajo el {@code lease} dado y la
     * devuelve ya decodificada. Vacío si no había ninguna.
     */
    Optional<TareaReclamada> reclamarSiguiente(String lease);

    /** Cierra la tarea reclamada: COMPLETADA si {@code exito}, si no FALLIDA. */
    void finalizar(TareaReclamada tarea, boolean exito);
}
