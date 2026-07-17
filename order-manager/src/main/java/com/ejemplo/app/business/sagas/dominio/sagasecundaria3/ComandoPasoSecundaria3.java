package com.ejemplo.app.business.sagas.dominio.sagasecundaria3;

import com.ejemplo.app.business.ordermanager.dominio.ComandoPaso;
import com.ejemplo.app.business.ordermanager.dominio.ExternalId;
import com.ejemplo.app.business.sagas.dominio.comun.RefPaso7;

/** Lo que la saga secundaria 3 ordena ejecutar: una llamada REST. */
public sealed interface ComandoPasoSecundaria3 extends ComandoPaso {

    record Ejecutar(ExternalId externalId, RefPaso7 refPaso7) implements ComandoPasoSecundaria3 {}
}
