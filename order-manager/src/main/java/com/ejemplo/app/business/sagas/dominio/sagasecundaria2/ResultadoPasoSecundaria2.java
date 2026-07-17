package com.ejemplo.app.business.sagas.dominio.sagasecundaria2;

import com.ejemplo.app.business.ordermanager.dominio.ResultadoPaso;

/** Lo que produce la saga secundaria 2 cuando llega el evento Kafka de respuesta. */
public sealed interface ResultadoPasoSecundaria2 extends ResultadoPaso {

    record Respuesta(RefRespuesta ref) implements ResultadoPasoSecundaria2 {}
}
