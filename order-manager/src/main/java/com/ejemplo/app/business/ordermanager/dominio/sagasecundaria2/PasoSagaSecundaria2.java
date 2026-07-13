package com.ejemplo.app.business.ordermanager.dominio.sagasecundaria2;

import com.ejemplo.app.business.ordermanager.dominio.comun.PasoSaga;

/**
 * Paso único de la saga secundaria 2: una llamada REST cuya respuesta llega
 * a posteriori como evento Kafka (puede tardar).
 */
public enum PasoSagaSecundaria2 implements PasoSaga {
    SOLICITUD
}
