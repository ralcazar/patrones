package com.ejemplo.app.business.ordermanager.dominio.sagaprincipal;

import org.jmolecules.ddd.annotation.ValueObject;

/** Dato de negocio que consume el PASO7. */
@ValueObject
public record DatoNegocio2(String valor1, String valor2) {}
