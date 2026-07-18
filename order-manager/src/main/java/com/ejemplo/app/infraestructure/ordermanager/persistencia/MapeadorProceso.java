package com.ejemplo.app.infraestructure.ordermanager.persistencia;

import java.util.List;
import java.util.UUID;

import com.ejemplo.app.business.ordermanager.dominio.AuditoriaIntervencion;
import com.ejemplo.app.business.ordermanager.dominio.ExternalId;
import com.ejemplo.app.business.ordermanager.dominio.Proceso;
import com.ejemplo.app.business.ordermanager.dominio.OrdenId;
import com.ejemplo.app.business.ordermanager.dominio.TipoOrden;

/**
 * SPI de persistencia por tipo de orden: {@link AdaptadorRepositorioOrden}
 * indexa las implementaciones por {@link #tipo()} y delega en ellas el
 * (des)armado de la forma persistible de cada tipo concreto, sin conocer sus
 * clases. Cada tipo guarda su propio contexto (las refs/datos que acumula
 * paso a paso) en SU tabla satélite relacional (ya no en el CLOB
 * {@code proceso.contexto}), con PK = el mismo {@code orden_id} de
 * {@code proceso}.
 */
public interface MapeadorProceso {

    TipoOrden tipo();

    /** El estado de la FSM de negocio, para la columna {@code proceso.estado}. */
    String estado(Proceso<?> proceso);

    /** Upsert del contexto propio del tipo en SU tabla satélite, en la misma transacción. */
    void guardarContexto(Proceso<?> proceso);

    /** Reconstruye el proceso leyendo SU satélite por {@code id} (el PK de proceso). */
    Proceso<?> rearmar(OrdenId id, ExternalId externalId, String estado, List<AuditoriaIntervencion> auditoria);

    /** Para la purga: DELETE en SU satélite; las filas de otros tipos simplemente no matchean. */
    void borrarContexto(List<UUID> ordenIds);
}
