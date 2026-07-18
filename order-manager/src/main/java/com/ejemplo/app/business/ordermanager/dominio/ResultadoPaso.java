package com.ejemplo.app.business.ordermanager.dominio;

/**
 * Marcadora de lo que cada paso produce al completarse. Cada agregado define
 * su sealed propio (ResultadoPasoPrincipal, ResultadoPasoSecundaria1...) con
 * switches exhaustivos dentro del proceso.
 */
public interface ResultadoPaso {
}
