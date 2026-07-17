package com.ejemplo.app.business.sagas.dominio.comun;

import org.jmolecules.ddd.annotation.ValueObject;

/** Referencia que produce el PASO5 al completarse. */
@ValueObject
public record RefPaso5(String valor) {}
