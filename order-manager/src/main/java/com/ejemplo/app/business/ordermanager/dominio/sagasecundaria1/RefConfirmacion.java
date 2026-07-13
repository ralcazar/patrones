package com.ejemplo.app.business.ordermanager.dominio.sagasecundaria1;

import org.jmolecules.ddd.annotation.ValueObject;

/** Confirmación que produce CONFIRMACION al cerrar la saga secundaria 1. */
@ValueObject
public record RefConfirmacion(String valor) {}
