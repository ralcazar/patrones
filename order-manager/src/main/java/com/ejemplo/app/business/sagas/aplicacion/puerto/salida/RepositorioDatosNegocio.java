package com.ejemplo.app.business.sagas.aplicacion.puerto.salida;

import java.util.List;
import java.util.Optional;

import com.ejemplo.app.business.ordermanager.dominio.ExternalId;
import com.ejemplo.app.business.sagas.dominio.datosnegocio.DatosNegocio;
import com.ejemplo.app.business.sagas.dominio.datosnegocio.DatosNegocioId;
import com.ejemplo.app.business.sagas.dominio.datosnegocio.DocumentoNegocio;

/** ÚNICO puerto de persistencia del agregado {@link DatosNegocio} (y sus {@link DocumentoNegocio}). */
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

    /**
     * Ids de los {@code datos_negocio} de esos externalIds que TODAVÍA no
     * tienen sellado {@code purgado_en} (para no reprocesar en pasadas
     * sucesivas de la purga de adjuntos). UNA query por lote: evita el N+1
     * de resolver externalId a externalId con {@link #buscarPorExternalId}.
     */
    List<DatosNegocioId> idsPorExternalIdsSinPurgar(List<ExternalId> externalIds);

    /**
     * Purga de adjuntos (sin borrar filas): pone a NULL el {@code contenido}
     * de todos los documentos del datos_negocio y sella
     * {@code datos_negocio.purgado_en} con el instante de la purga. Seguro
     * de repetir sobre un id ya purgado (NULL sobre NULL); en la práctica no
     * se reinvoca porque {@link #idsPorExternalIdsSinPurgar} ya lo excluye.
     */
    void purgarAdjuntos(DatosNegocioId id);
}
