package com.ejemplo.app.business.sagas.dominio.sagasecundaria1;

/** FSM de negocio de la saga secundaria 1: INICIO -&gt; CONFIRMACION, ambos síncronos. */
public enum EstadoSagaSecundaria1 {
    INICIAL,
    INICIO_HECHO,
    TERMINADA
}
