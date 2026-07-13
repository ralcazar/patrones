package com.ejemplo.app.business.ordermanager.dominio.sagasecundaria1;

import com.ejemplo.app.business.ordermanager.dominio.comun.ComandoPaso;
import com.ejemplo.app.business.ordermanager.dominio.comun.ExternalId;
import com.ejemplo.app.business.ordermanager.dominio.comun.RefPaso1;

/** Lo que la saga secundaria 1 ordena ejecutar: dos llamadas REST a métodos distintos del mismo servicio. */
public sealed interface ComandoPasoSecundaria1 extends ComandoPaso {

    record Iniciar(ExternalId externalId, RefPaso1 refPaso1) implements ComandoPasoSecundaria1 {}

    record Confirmar(RefInicio refInicio) implements ComandoPasoSecundaria1 {}
}
