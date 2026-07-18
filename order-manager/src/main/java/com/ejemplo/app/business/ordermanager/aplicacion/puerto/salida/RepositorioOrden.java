package com.ejemplo.app.business.ordermanager.aplicacion.puerto.salida;

import java.time.Instant;
import java.util.List;

import org.jmolecules.ddd.annotation.Repository;

import com.ejemplo.app.business.ordermanager.dominio.OrdenRoot;
import com.ejemplo.app.business.ordermanager.dominio.OrdenId;
import com.ejemplo.app.business.ordermanager.dominio.TipoOrden;

/**
 * ÚNICO puerto de persistencia de escritura del agregado: OrdenRoot, que
 * contiene su Proceso. Siempre persiste o rehidrata el agregado completo
 * (ejecución + negocio + auditoría) de una sola vez.
 */
@Repository
public interface RepositorioOrden {

    /** Persiste el agregado completo (orden + proceso + auditoría) por primera vez. */
    void crear(OrdenRoot orden);

    /** Rehidrata el agregado completo, despachando la subclase de Proceso por su tipo. */
    OrdenRoot cargar(OrdenId id);

    /**
     * Lanza ConcurrenciaOptimistaException si la versión no coincide. Devuelve
     * el agregado tal como queda persistido, con su version real actualizada,
     * para que el llamante pueda seguir operando sobre él sin recargar de BD
     * ni asumir cómo incrementa la version el motor de persistencia.
     */
    OrdenRoot guardar(OrdenRoot orden);

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

    record CandidataOrden(OrdenId ordenId, TipoOrden tipo) {}
}
