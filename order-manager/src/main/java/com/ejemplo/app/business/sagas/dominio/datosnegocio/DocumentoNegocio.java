package com.ejemplo.app.business.sagas.dominio.datosnegocio;

import org.jmolecules.ddd.annotation.ValueObject;

/**
 * Value Object hijo de {@link DatosNegocio}: un blob con su metadato, sin
 * identidad propia (no existe ninguna operación que direccione un documento
 * concreto por id; {@link com.ejemplo.app.business.sagas.aplicacion.puerto.salida.RepositorioDatosNegocio}
 * siempre los trata como {@code List<DocumentoNegocio>} completa — se crean,
 * cargan y borran en bloque junto con el {@code DatosNegocio} que los
 * contiene). La posición en esa lista (persistida como "secuencia" en
 * {@code DocumentoNegocioEntity}) es un detalle de infraestructura para
 * mantener el orden, no una identidad de dominio.
 */
@ValueObject
public record DocumentoNegocio(String nombre, String mimeType, byte[] contenido) {
}
