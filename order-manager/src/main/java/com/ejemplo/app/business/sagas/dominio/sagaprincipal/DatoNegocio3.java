package com.ejemplo.app.business.sagas.dominio.sagaprincipal;

import org.jmolecules.ddd.annotation.ValueObject;

/** Datos de negocio con los que arranca la tramitación; los consume el PASO1. */
@ValueObject
public record DatoNegocio3(String valor1, String valor2) {}
