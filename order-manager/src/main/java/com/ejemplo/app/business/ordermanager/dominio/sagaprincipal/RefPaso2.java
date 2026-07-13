package com.ejemplo.app.business.ordermanager.dominio.sagaprincipal;

import org.jmolecules.ddd.annotation.ValueObject;

/** Referencia que produce el PASO2 al completarse. */
@ValueObject
public record RefPaso2(String valor) {}
