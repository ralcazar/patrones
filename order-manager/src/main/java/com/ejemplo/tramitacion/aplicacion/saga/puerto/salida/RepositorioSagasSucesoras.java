package com.ejemplo.tramitacion.aplicacion.saga.puerto.salida;

import java.time.Instant;

import com.ejemplo.tramitacion.dominio.saga.general.SagaId;
import com.ejemplo.tramitacion.dominio.saga.general.SagaSucesora;

/**
 * No existe un "recuperar pendientes de arranque": la tarea ArrancarSaga se
 * encola en la misma transacción que crea la saga, y el lease del GestorOrdenes
 * garantiza su reentrega si el proceso muere.
 */
public interface RepositorioSagasSucesoras {
    void crear(SagaSucesora saga);
    /** Carga polimórfica: devuelve SagaAsincrona, SagaSecuencial o SagaSimple según el tipo persistido. */
    SagaSucesora cargar(SagaId id);
    /** Lanza ConcurrenciaOptimistaException si la versión no coincide. */
    void guardar(SagaSucesora saga);
    /**
     * Limpieza de datos: borra las sucesoras COMPLETADAs cuya última
     * actualización es anterior al corte. Devuelve cuántas borró.
     */
    long purgarFinalizadasAntesDe(Instant corte);
}
