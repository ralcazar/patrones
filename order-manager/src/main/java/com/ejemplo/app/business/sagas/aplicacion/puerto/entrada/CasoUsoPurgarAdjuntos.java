package com.ejemplo.app.business.sagas.aplicacion.puerto.entrada;

import java.time.Instant;

/**
 * Purga diaria de adjuntos: para cada tramitación (grupo de las 4 sagas que
 * comparten {@code external_id}) totalmente terminada antes de {@code corte},
 * anula el contenido de los documentos de su {@code datos_negocio} SIN
 * borrar filas (ver
 * {@link com.ejemplo.app.business.sagas.aplicacion.puerto.salida.RepositorioDatosNegocio#purgarAdjuntos}).
 * Lo invoca un planificador de infraestructura una vez al día, que calcula
 * {@code corte} a partir de la retención configurable (por defecto 30 días;
 * ver {@code PlanificadorPurgaAdjuntos}) -- el mismo contrato que
 * {@code CasoUsoLimpiarDatosAntiguos.purgarAnterioresA}.
 */
public interface CasoUsoPurgarAdjuntos {

    /**
     * Ejecuta la purga de tramitaciones terminadas antes de {@code corte}
     * (con reintento operativo interno; si agota reintentos, abre una
     * incidencia). Devuelve el número de {@code datos_negocio} purgados.
     */
    long purgarAdjuntos(Instant corte);
}
