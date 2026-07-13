package com.ejemplo.app.business.ordermanager.aplicacion.puerto.salida;

import java.util.List;

import com.ejemplo.app.business.ordermanager.aplicacion.puerto.entrada.CasoUsoConsultarSagasSoporte.FiltroSagas;
import com.ejemplo.app.business.ordermanager.aplicacion.puerto.entrada.CasoUsoConsultarSagasSoporte.SagaDetalle;
import com.ejemplo.app.business.ordermanager.aplicacion.puerto.entrada.CasoUsoConsultarSagasSoporte.SagaResumen;
import com.ejemplo.app.business.ordermanager.aplicacion.puerto.entrada.CasoUsoConsultarSagasSoporte.VistaTramitacion;
import com.ejemplo.app.business.ordermanager.dominio.comun.ExternalId;
import com.ejemplo.app.business.ordermanager.dominio.comun.SagaId;
import com.ejemplo.app.business.ordermanager.dominio.comun.TipoSaga;

/**
 * Modelo de lectura para la pantalla de soporte: el adaptador lo implementa
 * con queries directas sobre las tablas (CQRS ligero), sin cargar agregados.
 */
public interface PuertoConsultaSagasSoporte {
    List<SagaResumen> sagasBloqueadas();
    /** Sagas con estado INICIADA o EN_CURSO, de todos los tipos. */
    List<SagaResumen> sagasEnEjecucion();
    /** WHERE dinámico sobre las tablas de sagas; los criterios a null no aplican. */
    List<SagaResumen> buscar(FiltroSagas filtro);
    VistaTramitacion vistaTramitacion(ExternalId externalId);
    SagaDetalle detalle(TipoSaga tipo, SagaId id);
}
