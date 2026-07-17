package com.ejemplo.app.business.sagas.dominio.sagasecundaria1;

import org.jmolecules.ddd.annotation.ValueObject;

/** Referencia que produce INICIO; la consume CONFIRMACION. */
@ValueObject
public record RefInicio(String valor) {}
