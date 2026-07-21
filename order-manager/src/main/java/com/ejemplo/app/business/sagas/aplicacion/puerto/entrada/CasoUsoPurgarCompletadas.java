package com.ejemplo.app.business.sagas.aplicacion.puerto.entrada;

/**
 * Purga diaria de tramitaciones completadas (corte 180 días): para cada
 * tramitación (grupo de las 4 sagas que comparten {@code external_id})
 * totalmente terminada hace ya más del corte, BORRA el agregado completo --
 * {@code datos_negocio} + documentos y las 4 órdenes (satélites + auditoría).
 * Lo invoca un planificador de infraestructura una vez al día.
 */
public interface CasoUsoPurgarCompletadas {

    /** Ejecuta la purga (con reintento operativo interno); si agota reintentos, abre una incidencia. */
    void purgarCompletadas();
}
