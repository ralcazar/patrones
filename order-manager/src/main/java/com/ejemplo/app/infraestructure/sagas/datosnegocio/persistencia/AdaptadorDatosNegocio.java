package com.ejemplo.app.infraestructure.sagas.datosnegocio.persistencia;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Component;

import com.ejemplo.app.business.ordermanager.dominio.ExternalId;
import com.ejemplo.app.business.sagas.aplicacion.puerto.salida.RepositorioDatosNegocio;
import com.ejemplo.app.business.sagas.dominio.datosnegocio.DatoNegocio1;
import com.ejemplo.app.business.sagas.dominio.datosnegocio.DatoNegocio2;
import com.ejemplo.app.business.sagas.dominio.datosnegocio.DatoNegocio3;
import com.ejemplo.app.business.sagas.dominio.datosnegocio.DatosNegocio;
import com.ejemplo.app.business.sagas.dominio.datosnegocio.DatosNegocioId;
import com.ejemplo.app.business.sagas.dominio.datosnegocio.DocumentoNegocio;
import com.ejemplo.app.business.sagas.dominio.datosnegocio.ExternalIdDuplicadoException;

/** Único adaptador de escritura/lectura del agregado {@link DatosNegocio}. */
@Component
public class AdaptadorDatosNegocio implements RepositorioDatosNegocio {

    private final DatosNegocioJpaRepository datosNegocio;
    private final DocumentoNegocioJpaRepository documentos;

    public AdaptadorDatosNegocio(DatosNegocioJpaRepository datosNegocio, DocumentoNegocioJpaRepository documentos) {
        this.datosNegocio = datosNegocio;
        this.documentos = documentos;
    }

    @Override
    public void crear(DatosNegocio datosNegocioAGuardar, List<DocumentoNegocio> documentosAGuardar) {
        try {
            datosNegocio.save(entidadDatosNegocioDe(datosNegocioAGuardar));
            // Fuerza el chequeo del índice único aquí, no en el commit de fuera: crear() se
            // invoca desde dentro de la transacción de ServicioIniciarTramitacion.crearAgregados,
            // así que sin este flush la violación se diferiría hasta ESE commit y llegaría como
            // DataIntegrityViolationException cruda, sin traducir (mismo motivo que
            // AdaptadorRepositorioOrden.guardar() fuerza ordenes.flush()).
            datosNegocio.flush();
        } catch (DataIntegrityViolationException e) {
            throw new ExternalIdDuplicadoException(datosNegocioAGuardar.externalId(), e);
        }
        var id = datosNegocioAGuardar.id().valor();
        for (var i = 0; i < documentosAGuardar.size(); i++) {
            documentos.save(entidadDocumentoDe(id, i, documentosAGuardar.get(i)));
        }
    }

    @Override
    public DatosNegocio cargar(DatosNegocioId id) {
        var entity = datosNegocio.findById(id.valor())
                .orElseThrow(() -> new IllegalArgumentException("No existen los datos de negocio " + id.valor()));
        return datosNegocioDesde(entity);
    }

    @Override
    public List<DocumentoNegocio> documentosDe(DatosNegocioId id) {
        return documentos.findByDatosnegocioIdOrderBySecuenciaAsc(id.valor()).stream()
                .map(AdaptadorDatosNegocio::documentoDesde)
                .toList();
    }

    @Override
    public Optional<DatosNegocio> buscarPorExternalId(ExternalId externalId) {
        return datosNegocio.findByExternalId(externalId.valor().toString())
                .map(AdaptadorDatosNegocio::datosNegocioDesde);
    }

    @Override
    public List<DatosNegocioId> idsHuerfanos() {
        return datosNegocio.idsHuerfanos().stream().map(UUID::fromString).map(DatosNegocioId::new).toList();
    }

    @Override
    public void borrar(DatosNegocioId id) {
        documentos.deleteByDatosnegocioId(id.valor()); // hija primero: sin ON DELETE CASCADE (ver CLAUDE.md)
        datosNegocio.deleteById(id.valor());
    }

    @Override
    public List<DatosNegocioId> idsPorExternalIdsSinPurgar(List<ExternalId> externalIds) {
        if (externalIds.isEmpty()) {
            return List.of();
        }
        var valores = externalIds.stream().map(id -> id.valor().toString()).toList();
        return datosNegocio.idsPorExternalIdsSinPurgar(valores).stream().map(UUID::fromString).map(DatosNegocioId::new).toList();
    }

    @Override
    public void purgarAdjuntos(DatosNegocioId id) {
        documentos.purgarContenidoDe(id.valor()); // contenido de los documentos primero, luego el sello del padre
        datosNegocio.sellarPurgadoEn(id.valor(), Instant.now());
    }

    // ------------------------------------------------------------------
    // DatosNegocioEntity <-> DatosNegocio
    // ------------------------------------------------------------------

    private static DatosNegocioEntity entidadDatosNegocioDe(DatosNegocio d) {
        // purgadoEn siempre NULL al crear: el sello lo pone la purga de adjuntos (fase aparte).
        return new DatosNegocioEntity(d.id().valor(), d.externalId().valor().toString(),
                d.datoNegocio1().valor(), d.datoNegocio2().valor(), d.datoNegocio3().valor(), null);
    }

    private static DatosNegocio datosNegocioDesde(DatosNegocioEntity entity) {
        return DatosNegocio.crear(new DatosNegocioId(entity.getDatosnegocioId()),
                ExternalId.de(entity.getExternalId()), new DatoNegocio1(entity.getDatoNegocio1()),
                new DatoNegocio2(entity.getDatoNegocio2()), new DatoNegocio3(entity.getDatoNegocio3()));
    }

    // ------------------------------------------------------------------
    // DocumentoNegocioEntity <-> DocumentoNegocio
    // ------------------------------------------------------------------

    private static DocumentoNegocioEntity entidadDocumentoDe(UUID id, int secuencia, DocumentoNegocio d) {
        return new DocumentoNegocioEntity(id, secuencia, d.nombre(), d.mimeType(), d.contenido());
    }

    private static DocumentoNegocio documentoDesde(DocumentoNegocioEntity entity) {
        return new DocumentoNegocio(entity.getNombre(), entity.getMimeType(), entity.getContenido());
    }
}
