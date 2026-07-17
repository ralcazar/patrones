package com.ejemplo.app.business.sagas.dominio.sagasecundaria3;

import com.ejemplo.app.business.ordermanager.dominio.ResultadoPaso;

/** Lo que produce el paso único de la saga secundaria 3. */
public sealed interface ResultadoPasoSecundaria3 extends ResultadoPaso {

    record Ejecutada(RefEjecucion ref) implements ResultadoPasoSecundaria3 {}
}
