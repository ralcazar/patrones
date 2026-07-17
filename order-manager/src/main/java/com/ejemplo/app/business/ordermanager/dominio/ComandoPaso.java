package com.ejemplo.app.business.ordermanager.dominio;

/**
 * Marcadora de lo que el dominio ordena ejecutar. Cada agregado define su
 * sealed propio (ComandoPasoPrincipal, ComandoPasoSecundaria1...): la
 * exhaustividad se recupera dentro de cada saga con un switch sobre su sealed.
 * No puede ser sealed aquí: las implementaciones viven en los paquetes de
 * cada agregado.
 */
public interface ComandoPaso {
}
