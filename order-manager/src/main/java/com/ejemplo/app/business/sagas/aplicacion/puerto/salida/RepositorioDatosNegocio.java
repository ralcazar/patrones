package com.ejemplo.app.business.sagas.aplicacion.puerto.salida;

import java.util.List;
import java.util.Optional;

import com.ejemplo.app.business.ordermanager.dominio.ExternalId;
import com.ejemplo.app.business.sagas.dominio.datosnegocio.DatosNegocio;
import com.ejemplo.app.business.sagas.dominio.datosnegocio.DatosNegocioId;
import com.ejemplo.app.business.sagas.dominio.datosnegocio.DocumentoNegocio;

public interface RepositorioDatosNegocio {
    void crear(DatosNegocio datosNegocio, List<DocumentoNegocio> documentos);

    /** Solo escalares: JAMÁS carga los blobs de los documentos. */
    DatosNegocio cargar(DatosNegocioId id);

    /** Única query que toca el BLOB. */
    List<DocumentoNegocio> documentosDe(DatosNegocioId id);

    /** Para idempotencia futura. */
    Optional<DatosNegocio> buscarPorExternalId(ExternalId externalId);
}
