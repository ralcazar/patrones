package com.ejemplo.app.business.ordermanager.dominio.sagaprincipal;

import org.jmolecules.ddd.annotation.ValueObject;

/** Referencia que produce el PASO6 al completarse. */
@ValueObject
public record RefPaso6(String valor) {}
