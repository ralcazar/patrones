package com.ejemplo.app.business.sagas.dominio.datosnegocio;

import org.jmolecules.ddd.annotation.ValueObject;

/** Uno de los tres escalares de negocio obtenidos al iniciar la tramitación (ver {@link DatosNegocio}). */
@ValueObject
public record DatoNegocio3(String valor) {
}
