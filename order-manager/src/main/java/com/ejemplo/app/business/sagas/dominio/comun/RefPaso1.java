package com.ejemplo.app.business.sagas.dominio.comun;

import org.jmolecules.ddd.annotation.ValueObject;

/** Referencia que produce el PASO1 al completarse. */
@ValueObject
public record RefPaso1(String valor) {}
