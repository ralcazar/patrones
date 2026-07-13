package com.ejemplo.app.business.ordermanager.dominio.comun;

/**
 * PRINCIPAL: la cadena de 8 pasos síncronos.
 * Al completarse arrancan tres sagas secundarias independientes:
 * SECUNDARIA1 (dos llamadas REST encadenadas al mismo servicio),
 * SECUNDARIA2 (una llamada REST cuya respuesta llega después como evento Kafka)
 * y SECUNDARIA3 (una llamada REST).
 */
public enum TipoSaga { PRINCIPAL, SECUNDARIA1, SECUNDARIA2, SECUNDARIA3 }
