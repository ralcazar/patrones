package com.ejemplo.app.business.ordermanager.aplicacion.puerto.entrada;

/**
 * Continúa la ejecución de sagas pendientes: reclama el token de ejecución
 * de la siguiente candidata elegible, avanza pasos síncronos hasta aparcar o
 * terminar, y persiste en cada paso. Lo invocan los workers pull
 * (una candidata por pull, ver {@link #continuarSiguiente()}); los eventos
 * que despiertan una orden (respuesta Kafka, intervención de soporte) solo
 * la marcan elegible de nuevo ({@code OrdenRoot.despertar}/
 * {@code programarReintento}) y es el propio modelo pull quien la recoge.
 */
public interface CasoUsoContinuarSaga {

    /**
     * Reclama y ejecuta la siguiente candidata elegible (modelo pull: lo invoca
     * cada worker al quedarse libre). Devuelve false si no queda trabajo.
     */
    boolean continuarSiguiente();

    /** ¿Hay alguna candidata elegible ahora mismo? (para que el planificador despierte workers). */
    boolean hayTrabajoPendiente();
}
