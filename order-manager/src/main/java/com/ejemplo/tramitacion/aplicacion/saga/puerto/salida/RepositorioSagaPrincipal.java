package com.ejemplo.tramitacion.aplicacion.saga.puerto.salida;

import java.time.Instant;

import com.ejemplo.tramitacion.dominio.saga.general.SagaId;
import com.ejemplo.tramitacion.dominio.saga.general.SagaPrincipal;

public interface RepositorioSagaPrincipal {
    void crear(SagaPrincipal saga);
    boolean existe(SagaId id);
    SagaPrincipal cargar(SagaId id);
    /** Lanza ConcurrenciaOptimistaException si la versión no coincide. */
    void guardar(SagaPrincipal saga);
    /**
     * Limpieza de datos: borra las sagas que acabaron bien (COMPLETADA o
     * CANCELADA) cuya última actualización es anterior al corte. Las EN_CURSO
     * o con pasos bloqueados no se tocan nunca. Devuelve cuántas borró.
     */
    long purgarFinalizadasAntesDe(Instant corte);
}
