package com.ejemplo.app.business.ordermanager.aplicacion.puerto.salida;

import java.time.Instant;
import java.util.List;

import org.jmolecules.ddd.annotation.Repository;

import com.ejemplo.app.business.ordermanager.dominio.comun.OrdenRoot;
import com.ejemplo.app.business.ordermanager.dominio.comun.SagaId;
import com.ejemplo.app.business.ordermanager.dominio.comun.TipoSaga;

/**
 * ÚNICO puerto de persistencia de escritura del agregado: OrdenRoot, que
 * contiene su Saga. Siempre persiste o rehidrata el agregado completo
 * (ejecución + negocio + auditoría) de una sola vez.
 */
@Repository
public interface RepositorioOrden {

    /** Persiste el agregado completo (orden + saga + auditoría) por primera vez. */
    void crear(OrdenRoot orden);

    /** Rehidrata el agregado completo, despachando la subclase de Saga por su tipo. */
    OrdenRoot cargar(SagaId id);

    /** Lanza ConcurrenciaOptimistaException si la versión no coincide. */
    void guardar(OrdenRoot orden);

    /**
     * Candidatas para el planificador: {@code proximo_reintento_en <= ahora AND
     * resultado IS NULL AND (token_trabajador IS NULL OR token_expira_en <= ahora)},
     * hasta {@code limite} filas.
     */
    List<CandidataOrden> buscarEjecutables(Instant ahora, int limite);

    /** ¿Existe alguna candidata elegible? Mismo predicado que buscarEjecutables, sin cargar filas. */
    boolean hayEjecutables(Instant ahora);

    /** Limpieza de datos: borra el agregado completo de las órdenes finalizadas antes del corte. */
    long purgarFinalizadasAntesDe(Instant corte);

    record CandidataOrden(SagaId sagaId, TipoSaga tipo) {}
}
