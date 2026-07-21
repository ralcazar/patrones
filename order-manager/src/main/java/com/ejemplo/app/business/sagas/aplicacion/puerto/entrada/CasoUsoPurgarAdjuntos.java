package com.ejemplo.app.business.sagas.aplicacion.puerto.entrada;

/**
 * Purga diaria de adjuntos (corte 30 días): para cada tramitación (grupo de
 * las 4 sagas que comparten {@code external_id}) totalmente terminada hace
 * ya más del corte, anula el contenido de los documentos de su
 * {@code datos_negocio} SIN borrar filas (ver
 * {@link com.ejemplo.app.business.sagas.aplicacion.puerto.salida.RepositorioDatosNegocio#purgarAdjuntos}).
 * Lo invoca un planificador de infraestructura una vez al día.
 */
public interface CasoUsoPurgarAdjuntos {

    /** Ejecuta la purga (con reintento operativo interno); si agota reintentos, abre una incidencia. */
    void purgarAdjuntos();
}
