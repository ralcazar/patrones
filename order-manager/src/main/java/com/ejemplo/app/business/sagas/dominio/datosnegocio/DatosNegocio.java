package com.ejemplo.app.business.sagas.dominio.datosnegocio;

import java.time.Instant;

import org.jmolecules.ddd.annotation.AggregateRoot;
import org.jmolecules.ddd.annotation.Identity;

import com.ejemplo.app.business.ordermanager.dominio.ExternalId;

/**
 * Agregado de los datos de negocio de una tramitación, correlacionado con la
 * saga principal por {@link ExternalId} (misma correlación externa que el
 * resto de sagas). Deliberadamente SIN lista de documentos en memoria: son
 * blobs y se piden aparte al repositorio ({@code documentosDe}), para no
 * cargarlos accidentalmente cada vez que se carga el agregado.
 *
 * Modela también {@code purgadoEn}: NULL mientras los documentos conservan su
 * contenido, sellado por {@link #purgar} con el {@code ahora} de la purga de
 * adjuntos (reloj determinista: el agregado nunca llama a
 * {@code Instant.now()}, lo aporta la capa de aplicación).
 */
@AggregateRoot
public final class DatosNegocio {

    @Identity
    private final DatosNegocioId id;
    private final ExternalId externalId;
    private final DatoNegocio1 datoNegocio1;
    private final DatoNegocio2 datoNegocio2;
    private final DatoNegocio3 datoNegocio3;
    private Instant purgadoEn;

    private DatosNegocio(DatosNegocioId id, ExternalId externalId, DatoNegocio1 datoNegocio1,
            DatoNegocio2 datoNegocio2, DatoNegocio3 datoNegocio3, Instant purgadoEn) {
        this.id = id;
        this.externalId = externalId;
        this.datoNegocio1 = datoNegocio1;
        this.datoNegocio2 = datoNegocio2;
        this.datoNegocio3 = datoNegocio3;
        this.purgadoEn = purgadoEn;
    }

    /** Alta: nace siempre sin purgar (el sello lo pone {@link #purgar}, en una fase aparte). */
    public static DatosNegocio crear(DatosNegocioId id, ExternalId externalId, DatoNegocio1 datoNegocio1,
            DatoNegocio2 datoNegocio2, DatoNegocio3 datoNegocio3) {
        return new DatosNegocio(id, externalId, datoNegocio1, datoNegocio2, datoNegocio3, null);
    }

    /** Para el adaptador de persistencia. */
    public static DatosNegocio rehidratar(DatosNegocioId id, ExternalId externalId, DatoNegocio1 datoNegocio1,
            DatoNegocio2 datoNegocio2, DatoNegocio3 datoNegocio3, Instant purgadoEn) {
        return new DatosNegocio(id, externalId, datoNegocio1, datoNegocio2, datoNegocio3, purgadoEn);
    }

    /** Sella la purga de adjuntos con el {@code ahora} recibido (reloj determinista). */
    public void purgar(Instant ahora) {
        this.purgadoEn = ahora;
    }

    public boolean estaPurgada() { return purgadoEn != null; }

    public DatosNegocioId id() { return id; }
    public ExternalId externalId() { return externalId; }
    public DatoNegocio1 datoNegocio1() { return datoNegocio1; }
    public DatoNegocio2 datoNegocio2() { return datoNegocio2; }
    public DatoNegocio3 datoNegocio3() { return datoNegocio3; }
    public Instant purgadoEn() { return purgadoEn; }
}
