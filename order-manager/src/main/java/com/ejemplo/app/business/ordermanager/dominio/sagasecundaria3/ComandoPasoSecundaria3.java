package com.ejemplo.app.business.ordermanager.dominio.sagasecundaria3;

import com.ejemplo.app.business.ordermanager.dominio.comun.ComandoPaso;
import com.ejemplo.app.business.ordermanager.dominio.comun.ExternalId;
import com.ejemplo.app.business.ordermanager.dominio.comun.RefPaso7;

/** Lo que la saga secundaria 3 ordena ejecutar: una llamada REST. */
public sealed interface ComandoPasoSecundaria3 extends ComandoPaso {

    record Ejecutar(ExternalId externalId, RefPaso7 refPaso7) implements ComandoPasoSecundaria3 {}
}
