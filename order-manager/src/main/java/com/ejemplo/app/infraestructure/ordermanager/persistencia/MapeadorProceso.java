package com.ejemplo.app.infraestructure.ordermanager.persistencia;

import java.util.List;
import java.util.Map;

import com.ejemplo.app.business.ordermanager.dominio.comun.AuditoriaIntervencion;
import com.ejemplo.app.business.ordermanager.dominio.comun.ExternalId;
import com.ejemplo.app.business.ordermanager.dominio.comun.Saga;
import com.ejemplo.app.business.ordermanager.dominio.comun.SagaId;
import com.ejemplo.app.business.ordermanager.dominio.comun.TipoSaga;

/**
 * SPI de persistencia por tipo de saga: {@link AdaptadorRepositorioOrden}
 * indexa las implementaciones por {@link #tipo()} y delega en ellas el
 * (des)armado de la forma persistible de cada saga concreta, sin conocer sus
 * clases. La codificación a JSON del contexto (común a todos los tipos) la
 * sigue haciendo el adaptador con {@link ContextoCodec}.
 */
public interface MapeadorProceso {

    TipoSaga tipo();

    ProcesoPersistible desarmar(Saga<?> saga);

    Saga<?> rearmar(SagaId id, ExternalId externalId, String estado,
            Map<String, String> contexto, List<AuditoriaIntervencion> auditoria);

    record ProcesoPersistible(String estado, Map<String, String> contexto) {}
}
