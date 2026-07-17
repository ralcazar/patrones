package com.ejemplo.app.infraestructure.ordermanager.persistencia;

import java.util.List;
import java.util.Map;

import com.ejemplo.app.business.ordermanager.dominio.AuditoriaIntervencion;
import com.ejemplo.app.business.ordermanager.dominio.ExternalId;
import com.ejemplo.app.business.ordermanager.dominio.Proceso;
import com.ejemplo.app.business.ordermanager.dominio.OrdenId;
import com.ejemplo.app.business.ordermanager.dominio.TipoOrden;

/**
 * SPI de persistencia por tipo de orden: {@link AdaptadorRepositorioOrden}
 * indexa las implementaciones por {@link #tipo()} y delega en ellas el
 * (des)armado de la forma persistible de cada tipo concreto, sin conocer sus
 * clases. La codificación a JSON del contexto (común a todos los tipos) la
 * sigue haciendo el adaptador con {@link ContextoCodec}.
 */
public interface MapeadorProceso {

    TipoOrden tipo();

    ProcesoPersistible desarmar(Proceso<?> proceso);

    Proceso<?> rearmar(OrdenId id, ExternalId externalId, String estado,
            Map<String, String> contexto, List<AuditoriaIntervencion> auditoria);

    record ProcesoPersistible(String estado, Map<String, String> contexto) {}
}
