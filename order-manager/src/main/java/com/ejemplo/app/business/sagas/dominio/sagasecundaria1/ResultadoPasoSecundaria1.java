package com.ejemplo.app.business.sagas.dominio.sagasecundaria1;

import com.ejemplo.app.business.ordermanager.dominio.comun.ResultadoPaso;

/** Lo que producen los pasos de la saga secundaria 1. */
public sealed interface ResultadoPasoSecundaria1 extends ResultadoPaso {

    record Iniciada(RefInicio ref) implements ResultadoPasoSecundaria1 {}

    record Confirmada(RefConfirmacion ref) implements ResultadoPasoSecundaria1 {}
}
