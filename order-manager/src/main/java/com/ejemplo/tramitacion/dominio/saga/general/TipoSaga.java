package com.ejemplo.tramitacion.dominio.saga.general;

/**
 * PRINCIPAL: la cadena de 8 pasos síncronos.
 * Al completarse arrancan tres sagas independientes:
 * ASINCRONA (un paso vía mensajería), SECUENCIAL (dos pasos encadenados)
 * y SIMPLE (un paso síncrono).
 */
public enum TipoSaga { PRINCIPAL, ASINCRONA, SECUENCIAL, SIMPLE }
