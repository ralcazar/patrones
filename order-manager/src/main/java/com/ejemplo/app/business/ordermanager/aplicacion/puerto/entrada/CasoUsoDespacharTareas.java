package com.ejemplo.app.business.ordermanager.aplicacion.puerto.entrada;

import java.util.Optional;

import com.ejemplo.app.business.ordermanager.aplicacion.tarea.TareaReclamada;

/**
 * Caso de uso del despacho de la cola: la puerta por la que los adaptadores de
 * entrada del pool (GestorOrdenes y TrabajadorOrdenes) empujan trabajo SIN tocar
 * la BBDD directamente. El adaptador aporta el CUÁNDO (sondeo, hilos, backoff,
 * logs); este caso de uso aporta el QUÉ (consultar, reclamar, procesar y cerrar),
 * apoyándose en {@code PuertoRecepcionTareas} y el {@code ManejadorTareasSaga}.
 *
 * Las tres operaciones se exponen por separado a propósito: el trabajador aplica
 * una política de errores distinta a cada una (fallo al reclamar -> se detiene
 * hasta el próximo ciclo; fallo al procesar -> orden FALLIDA y sigue drenando;
 * fallo al finalizar -> el lease caduca y otro la retoma).
 */
public interface CasoUsoDespacharTareas {

    /** ¿Hay trabajo elegible ahora mismo? Lo usa el sondeo para no despertar al pool en vano. */
    boolean hayTrabajoPendiente();

    /** Reclama la siguiente tarea bajo el {@code lease} dado; vacío si la cola está vacía. */
    Optional<TareaReclamada> reclamarSiguiente(String lease);

    /** Procesa la tarea reclamada (continuar la saga). Es idempotente ante reentrega por lease. */
    void procesar(TareaReclamada tarea);

    /** Cierra la tarea: COMPLETADA si {@code exito}, si no FALLIDA. */
    void finalizar(TareaReclamada tarea, boolean exito);
}
