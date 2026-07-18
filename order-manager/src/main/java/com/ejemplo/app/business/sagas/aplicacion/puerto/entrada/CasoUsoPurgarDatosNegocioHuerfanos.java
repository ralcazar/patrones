package com.ejemplo.app.business.sagas.aplicacion.puerto.entrada;

/**
 * Purga periódica de {@code datos_negocio} huérfanos: los que ya no tienen
 * ningún {@code proceso} (de las 4 sagas de la tramitación) que comparta su
 * externalId, porque la limpieza del motor purgó la tramitación completa.
 * Lo invoca un planificador de infraestructura cada cierto tiempo.
 */
public interface CasoUsoPurgarDatosNegocioHuerfanos {

    /** Purga todos los huérfanos actuales y devuelve el recuento borrado. */
    long purgarHuerfanos();
}
