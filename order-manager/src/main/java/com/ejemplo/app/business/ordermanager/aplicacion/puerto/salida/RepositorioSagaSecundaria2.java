package com.ejemplo.app.business.ordermanager.aplicacion.puerto.salida;

import java.time.Instant;

import org.jmolecules.ddd.annotation.Repository;

import com.ejemplo.app.business.ordermanager.dominio.comun.SagaId;
import com.ejemplo.app.business.ordermanager.dominio.sagasecundaria2.SagaSecundaria2Root;

@Repository
public interface RepositorioSagaSecundaria2 {
    void crear(SagaSecundaria2Root saga);
    SagaSecundaria2Root cargar(SagaId id);
    /** Lanza ConcurrenciaOptimistaException si la versión no coincide. */
    void guardar(SagaSecundaria2Root saga);
    /**
     * Limpieza de datos: borra las sagas COMPLETADAs cuya última
     * actualización es anterior al corte. Devuelve cuántas borró.
     */
    long purgarFinalizadasAntesDe(Instant corte);
}
