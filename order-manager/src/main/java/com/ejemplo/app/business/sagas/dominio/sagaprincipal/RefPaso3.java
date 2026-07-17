package com.ejemplo.app.business.sagas.dominio.sagaprincipal;

import org.jmolecules.ddd.annotation.ValueObject;

/** Referencia que produce el PASO3 al completarse. */
@ValueObject
public record RefPaso3(String valor) {}
