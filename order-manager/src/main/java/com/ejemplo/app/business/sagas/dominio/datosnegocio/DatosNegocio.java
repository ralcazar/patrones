package com.ejemplo.app.business.sagas.dominio.datosnegocio;

import org.jmolecules.ddd.annotation.AggregateRoot;
import org.jmolecules.ddd.annotation.Identity;

import com.ejemplo.app.business.ordermanager.dominio.ExternalId;

/**
 * Agregado de los datos de negocio de una tramitación, correlacionado con la
 * saga principal por {@link ExternalId} (misma correlación externa que el
 * resto de sagas). Deliberadamente SIN lista de documentos en memoria: son
 * blobs y se piden aparte al repositorio ({@code documentosDe}), para no
 * cargarlos accidentalmente cada vez que se carga el agregado.
 */
@AggregateRoot
public final class DatosNegocio {

    @Identity
    private final DatosNegocioId id;
    private final ExternalId externalId;
    private final DatoNegocio1 datoNegocio1;
    private final DatoNegocio2 datoNegocio2;
    private final DatoNegocio3 datoNegocio3;

    private DatosNegocio(DatosNegocioId id, ExternalId externalId, DatoNegocio1 datoNegocio1,
            DatoNegocio2 datoNegocio2, DatoNegocio3 datoNegocio3) {
        this.id = id;
        this.externalId = externalId;
        this.datoNegocio1 = datoNegocio1;
        this.datoNegocio2 = datoNegocio2;
        this.datoNegocio3 = datoNegocio3;
    }

    public static DatosNegocio crear(DatosNegocioId id, ExternalId externalId, DatoNegocio1 datoNegocio1,
            DatoNegocio2 datoNegocio2, DatoNegocio3 datoNegocio3) {
        return new DatosNegocio(id, externalId, datoNegocio1, datoNegocio2, datoNegocio3);
    }

    public DatosNegocioId id() { return id; }
    public ExternalId externalId() { return externalId; }
    public DatoNegocio1 datoNegocio1() { return datoNegocio1; }
    public DatoNegocio2 datoNegocio2() { return datoNegocio2; }
    public DatoNegocio3 datoNegocio3() { return datoNegocio3; }
}
