package com.ejemplo.app.business.ordermanager.aplicacion.puerto.salida;

import org.jmolecules.ddd.annotation.Repository;

import java.time.Instant;

import com.ejemplo.app.business.ordermanager.dominio.comun.SagaId;
import com.ejemplo.app.business.ordermanager.dominio.sagaprincipal.SagaPrincipalRoot;

@Repository
public interface RepositorioSagaPrincipal {
    void crear(SagaPrincipalRoot saga);
    boolean existe(SagaId id);
    SagaPrincipalRoot cargar(SagaId id);
    /** Lanza ConcurrenciaOptimistaException si la versión no coincide. */
    void guardar(SagaPrincipalRoot saga);
    /**
     * Limpieza de datos: borra las sagas que acabaron bien (COMPLETADA o
     * CANCELADA) cuya última actualización es anterior al corte. Las EN_CURSO
     * o con pasos bloqueados no se tocan nunca. Devuelve cuántas borró.
     */
    long purgarFinalizadasAntesDe(Instant corte);
}
