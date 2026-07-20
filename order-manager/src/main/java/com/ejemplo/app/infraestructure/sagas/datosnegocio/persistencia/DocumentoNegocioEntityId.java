package com.ejemplo.app.infraestructure.sagas.datosnegocio.persistencia;

import java.io.Serializable;
import java.util.Objects;
import java.util.UUID;

/** Clave primaria compuesta de {@link DocumentoNegocioEntity} (patrón estándar JPA {@code @IdClass}). */
public class DocumentoNegocioEntityId implements Serializable {

    private UUID datosnegocioId;
    private Integer secuencia;

    public DocumentoNegocioEntityId() {
        // requerido por JPA
    }

    public DocumentoNegocioEntityId(UUID datosnegocioId, Integer secuencia) {
        this.datosnegocioId = datosnegocioId;
        this.secuencia = secuencia;
    }

    public UUID getDatosnegocioId() { return datosnegocioId; }
    public Integer getSecuencia() { return secuencia; }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof DocumentoNegocioEntityId that)) {
            return false;
        }
        return Objects.equals(datosnegocioId, that.datosnegocioId) && Objects.equals(secuencia, that.secuencia);
    }

    @Override
    public int hashCode() {
        return Objects.hash(datosnegocioId, secuencia);
    }
}
