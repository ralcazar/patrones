package com.ejemplo.app.infraestructure.sagas.datosnegocio.persistencia;

import java.util.UUID;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Lob;
import jakarta.persistence.Table;

/** Documento (blob) de un {@link DatosNegocioEntity}, PK compuesta (datosnegocio_id, secuencia). */
@Entity
@Table(name = "datos_negocio_documento")
@IdClass(DocumentoNegocioEntityId.class)
public class DocumentoNegocioEntity {

    @Id
    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(name = "datosnegocio_id", length = 36)
    private UUID datosnegocioId;

    @Id
    @Column(name = "secuencia")
    private Integer secuencia;

    @Column(name = "nombre", nullable = false, length = 200)
    private String nombre;

    @Column(name = "mime_type", nullable = false, length = 100)
    private String mimeType;

    // Anulable: la purga de adjuntos pone el contenido a NULL sin borrar la fila
    // (conserva nombre/mime_type), ver DatosNegocioEntity.purgadoEn.
    @Lob
    @Column(name = "contenido", nullable = true)
    private byte[] contenido;

    protected DocumentoNegocioEntity() {
        // requerido por JPA
    }

    public DocumentoNegocioEntity(UUID datosnegocioId, Integer secuencia, String nombre, String mimeType,
            byte[] contenido) {
        this.datosnegocioId = datosnegocioId;
        this.secuencia = secuencia;
        this.nombre = nombre;
        this.mimeType = mimeType;
        this.contenido = contenido;
    }

    public UUID getDatosnegocioId() { return datosnegocioId; }
    public Integer getSecuencia() { return secuencia; }
    public String getNombre() { return nombre; }
    public String getMimeType() { return mimeType; }
    public byte[] getContenido() { return contenido; }
}
