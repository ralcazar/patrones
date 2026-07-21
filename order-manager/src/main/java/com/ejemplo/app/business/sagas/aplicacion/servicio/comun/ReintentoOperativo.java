package com.ejemplo.app.business.sagas.aplicacion.servicio.comun;

import com.ejemplo.app.business.ordermanager.aplicacion.puerto.salida.PuertoIncidencias;

/**
 * Reintento operativo de tareas de fondo (las purgas por tramitación):
 * DISTINTO de {@code ReintentoOptimista} (motor,
 * {@code business.ordermanager.aplicacion.servicio}, que reintenta solo ante
 * conflicto de versión y siempre relanza si se agota). Este reintenta ante
 * CUALQUIER fallo operativo ({@link RuntimeException}) y, si agota los
 * reintentos, abre una incidencia con la causa del último fallo en vez de
 * relanzarlo: un fallo de purga no debe tumbar al planificador que la invoca.
 */
final class ReintentoOperativo {

    private static final int MAX_REINTENTOS = 5;

    private ReintentoOperativo() {}

    /**
     * Reintenta {@code accion} hasta {@value #MAX_REINTENTOS} veces. Cada
     * intento es independiente (típicamente una llamada a través de
     * {@code self} a un método {@code @Transactional}): si uno falla, el
     * siguiente repite la acción completa desde el principio.
     */
    static void ejecutar(String tarea, PuertoIncidencias incidencias, Runnable accion) {
        RuntimeException ultima = null;
        for (var intento = 0; intento < MAX_REINTENTOS; intento++) {
            try {
                accion.run();
                return;
            } catch (RuntimeException e) {
                ultima = e;
            }
        }
        incidencias.abrir(tarea, causaDe(ultima), MAX_REINTENTOS);
    }

    private static String causaDe(RuntimeException e) {
        return e.getClass().getSimpleName() + ": " + e.getMessage();
    }
}
