package com.ejemplo.app.business.sagas.dominio.datosnegocio;

import com.ejemplo.app.business.ordermanager.dominio.ExternalId;

/**
 * La lanza el adaptador de persistencia cuando el índice único de
 * {@code datos_negocio.external_id} rechaza un {@code crear} porque ya existe
 * una fila con ese externalId (carrera de dos POST /tramitaciones
 * simultáneos para la misma tramitación). Traduce a vocabulario de negocio
 * el {@code DataIntegrityViolationException} de Spring, que es infraestructura
 * y no puede cruzar a business (mismo patrón que
 * {@link com.ejemplo.app.business.ordermanager.dominio.ConcurrenciaOptimistaException}
 * con {@code OptimisticLockingFailureException}).
 */
public class ExternalIdDuplicadoException extends RuntimeException {
    public ExternalIdDuplicadoException(ExternalId externalId, Throwable causa) {
        super("Ya existen datos de negocio para el externalId " + externalId.valor(), causa);
    }
}
