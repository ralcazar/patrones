package com.ejemplo.app.business.sagas.aplicacion.puerto.entrada;

import java.time.Instant;

/**
 * Purga diaria de tramitaciones completadas: para cada tramitación (grupo de
 * las 4 sagas que comparten {@code external_id}) totalmente terminada antes
 * de {@code corte}, BORRA el agregado completo -- {@code datos_negocio} +
 * documentos y las 4 órdenes (satélites + auditoría). Lo invoca un
 * planificador de infraestructura una vez al día, que calcula {@code corte}
 * a partir de la retención configurable (por defecto 180 días; ver
 * {@code PlanificadorPurgaCompletadas}).
 */
public interface CasoUsoPurgarCompletadas {

    /**
     * Ejecuta la purga de tramitaciones terminadas antes de {@code corte}
     * (con reintento operativo interno; si agota reintentos, abre una
     * incidencia). Devuelve el número de tramitaciones purgadas.
     */
    long purgarCompletadas(Instant corte);
}
