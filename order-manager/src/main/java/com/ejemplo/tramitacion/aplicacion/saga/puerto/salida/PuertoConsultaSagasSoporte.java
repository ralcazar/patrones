package com.ejemplo.tramitacion.aplicacion.saga.puerto.salida;

import java.util.List;

import com.ejemplo.tramitacion.aplicacion.saga.puerto.entrada.CasoUsoConsultarSagasSoporte.FiltroSagas;
import com.ejemplo.tramitacion.aplicacion.saga.puerto.entrada.CasoUsoConsultarSagasSoporte.SagaDetalle;
import com.ejemplo.tramitacion.aplicacion.saga.puerto.entrada.CasoUsoConsultarSagasSoporte.SagaResumen;
import com.ejemplo.tramitacion.aplicacion.saga.puerto.entrada.CasoUsoConsultarSagasSoporte.VistaDatoNegocio1;
import com.ejemplo.tramitacion.dominio.saga.general.DatoNegocio1Id;
import com.ejemplo.tramitacion.dominio.saga.general.SagaId;
import com.ejemplo.tramitacion.dominio.saga.general.TipoSaga;

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
    VistaDatoNegocio1 vistaDatoNegocio1(DatoNegocio1Id datoNegocio1Id);
    SagaDetalle detalle(TipoSaga tipo, SagaId id);
}
