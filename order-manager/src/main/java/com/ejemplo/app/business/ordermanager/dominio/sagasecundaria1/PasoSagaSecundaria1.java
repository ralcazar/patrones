package com.ejemplo.app.business.ordermanager.dominio.sagasecundaria1;

import com.ejemplo.app.business.ordermanager.dominio.comun.PasoSaga;

/** Pasos de la saga secundaria 1: dos llamadas REST encadenadas al mismo servicio. */
public enum PasoSagaSecundaria1 implements PasoSaga {
    INICIO, CONFIRMACION
}
