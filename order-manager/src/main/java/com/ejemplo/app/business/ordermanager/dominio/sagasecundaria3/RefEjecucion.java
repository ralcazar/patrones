package com.ejemplo.app.business.ordermanager.dominio.sagasecundaria3;

import org.jmolecules.ddd.annotation.ValueObject;

/** Referencia que produce el paso EJECUCION. */
@ValueObject
public record RefEjecucion(String valor) {}
