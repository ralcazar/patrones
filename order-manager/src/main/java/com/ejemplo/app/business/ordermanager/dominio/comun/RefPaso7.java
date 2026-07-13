package com.ejemplo.app.business.ordermanager.dominio.comun;

import org.jmolecules.ddd.annotation.ValueObject;

/** Referencia que produce el PASO7 al completarse. */
@ValueObject
public record RefPaso7(String valor) {}
