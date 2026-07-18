package com.ejemplo.app.business.sagas.aplicacion.puerto.salida;

import java.util.List;
import java.util.Optional;

import com.ejemplo.app.business.ordermanager.dominio.ExternalId;
import com.ejemplo.app.business.sagas.dominio.datosnegocio.DatosNegocio;
import com.ejemplo.app.business.sagas.dominio.datosnegocio.DatosNegocioId;
import com.ejemplo.app.business.sagas.dominio.datosnegocio.DocumentoNegocio;

public interface RepositorioDatosNegocio {
    /**
     * Lanza {@link com.ejemplo.app.business.sagas.dominio.datosnegocio.ExternalIdDuplicadoException}
     * si el índice único de {@code external_id} rechaza la creación (carrera
     * de dos tramitaciones simultáneas para el mismo externalId).
     */
    void crear(DatosNegocio datosNegocio, List<DocumentoNegocio> documentos);

    /** Solo escalares: JAMÁS carga los blobs de los documentos. */
    DatosNegocio cargar(DatosNegocioId id);

    /** Única query que toca el BLOB. */
    List<DocumentoNegocio> documentosDe(DatosNegocioId id);

    /** Para idempotencia futura. */
    Optional<DatosNegocio> buscarPorExternalId(ExternalId externalId);

    /**
     * Ids de los {@code datos_negocio} sin ningún {@code proceso} que comparta
     * ya su externalId (las 4 sagas de la tramitación purgadas por completo).
     */
    List<DatosNegocioId> idsHuerfanos();

    /** Borra un datos_negocio huérfano: documentos primero (sin cascade), luego el propio registro. */
    void borrar(DatosNegocioId id);
}
