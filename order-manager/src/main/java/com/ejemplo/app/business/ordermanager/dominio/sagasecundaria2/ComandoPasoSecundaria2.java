package com.ejemplo.app.business.ordermanager.dominio.sagasecundaria2;

import com.ejemplo.app.business.ordermanager.dominio.comun.ComandoPaso;
import com.ejemplo.app.business.ordermanager.dominio.comun.ExternalId;
import com.ejemplo.app.business.ordermanager.dominio.comun.RefPaso5;

/** Lo que la saga secundaria 2 ordena ejecutar: la llamada REST de solicitud. */
public sealed interface ComandoPasoSecundaria2 extends ComandoPaso {

    record Solicitar(ExternalId externalId, RefPaso5 refPaso5) implements ComandoPasoSecundaria2 {}
}
