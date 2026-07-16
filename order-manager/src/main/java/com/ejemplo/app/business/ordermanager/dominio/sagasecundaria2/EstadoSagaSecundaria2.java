package com.ejemplo.app.business.ordermanager.dominio.sagasecundaria2;

/**
 * FSM de negocio de la saga secundaria 2: la solicitud es una llamada REST y
 * la respuesta llega a posteriori como evento Kafka.
 */
public enum EstadoSagaSecundaria2 {
    INICIAL,
    ESPERANDO_RESPUESTA,
    TERMINADA
}
