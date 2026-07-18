package com.ejemplo.app.business.sagas.aplicacion.puerto.salida;

import java.util.Optional;

import com.ejemplo.app.business.ordermanager.dominio.ExternalId;
import com.ejemplo.app.business.ordermanager.dominio.OrdenId;

/**
 * Búsqueda de la orden de la saga principal ya existente para un
 * {@link ExternalId} (idempotencia de {@code POST /tramitaciones}: dos
 * peticiones con el mismo externalId deben devolver la misma orden en vez de
 * crear una tramitación duplicada).
 */
public interface PuertoBusquedaTramitacion {
    Optional<OrdenId> ordenPrincipalDe(ExternalId externalId);
}
