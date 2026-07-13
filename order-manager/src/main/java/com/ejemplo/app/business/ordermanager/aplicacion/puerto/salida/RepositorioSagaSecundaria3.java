package com.ejemplo.app.business.ordermanager.aplicacion.puerto.salida;

import java.time.Instant;

import org.jmolecules.ddd.annotation.Repository;

import com.ejemplo.app.business.ordermanager.dominio.comun.SagaId;
import com.ejemplo.app.business.ordermanager.dominio.sagasecundaria3.SagaSecundaria3Root;

@Repository
public interface RepositorioSagaSecundaria3 {
    void crear(SagaSecundaria3Root saga);
    SagaSecundaria3Root cargar(SagaId id);
    /** Lanza ConcurrenciaOptimistaException si la versión no coincide. */
    void guardar(SagaSecundaria3Root saga);
    /**
     * Limpieza de datos: borra las sagas COMPLETADAs cuya última
     * actualización es anterior al corte. Devuelve cuántas borró.
     */
    long purgarFinalizadasAntesDe(Instant corte);
}
