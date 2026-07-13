package com.ejemplo.app.business.ordermanager.dominio.sagaprincipal;

import org.jmolecules.ddd.annotation.ValueObject;

/** Referencia que produce el PASO8 al completarse. */
@ValueObject
public record RefPaso8(String valor) {}
