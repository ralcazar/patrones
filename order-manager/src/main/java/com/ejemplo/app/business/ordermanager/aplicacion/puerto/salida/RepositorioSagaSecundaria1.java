package com.ejemplo.app.business.ordermanager.aplicacion.puerto.salida;

import java.time.Instant;

import org.jmolecules.ddd.annotation.Repository;

import com.ejemplo.app.business.ordermanager.dominio.comun.SagaId;
import com.ejemplo.app.business.ordermanager.dominio.sagasecundaria1.SagaSecundaria1Root;

/**
 * No existe un "recuperar pendientes de arranque": la tarea ArrancarSaga se
 * encola en la misma transacción que crea la saga, y el lease del GestorOrdenes
 * garantiza su reentrega si el proceso muere.
 */
@Repository
public interface RepositorioSagaSecundaria1 {
    void crear(SagaSecundaria1Root saga);
    SagaSecundaria1Root cargar(SagaId id);
    /** Lanza ConcurrenciaOptimistaException si la versión no coincide. */
    void guardar(SagaSecundaria1Root saga);
    /**
     * Limpieza de datos: borra las sagas COMPLETADAs cuya última
     * actualización es anterior al corte. Devuelve cuántas borró.
     */
    long purgarFinalizadasAntesDe(Instant corte);
}
