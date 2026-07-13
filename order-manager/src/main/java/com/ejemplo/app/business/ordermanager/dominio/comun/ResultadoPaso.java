package com.ejemplo.app.business.ordermanager.dominio.comun;

/**
 * Marcadora de lo que cada paso produce al completarse. Cada agregado define
 * su sealed propio (ResultadoPasoPrincipal, ResultadoPasoSecundaria1...) con
 * switches exhaustivos dentro de la saga.
 */
public interface ResultadoPaso {
}
