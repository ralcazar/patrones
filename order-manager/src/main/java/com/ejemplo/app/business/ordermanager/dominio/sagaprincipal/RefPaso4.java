package com.ejemplo.app.business.ordermanager.dominio.sagaprincipal;

import org.jmolecules.ddd.annotation.ValueObject;

/** Referencia que produce el PASO4 al completarse. */
@ValueObject
public record RefPaso4(String valor) {}
