package com.ejemplo.app.business.sagas.dominio.datosnegocio;

import org.jmolecules.ddd.annotation.Entity;

/** Entidad hija de {@link DatosNegocio}, sin agregado propio: un blob con su metadato. */
@Entity
public record DocumentoNegocio(String nombre, String mimeType, byte[] contenido) {
}
