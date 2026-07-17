package com.ejemplo.app.business.ordermanager.aplicacion.puerto.entrada;

/**
 * Barrido periódico de tickets: busca las órdenes con el flag "abrir ticket
 * pendiente", abre UN único ticket que las cubre a todas y las marca ABIERTO
 * con la fecha de apertura. Lo dispara el PlanificadorTicketsSoporte (cada 3h,
 * de 8 a 17). Reejecutarlo es inocuo: las órdenes ya ABIERTO no vuelven a salir.
 */
public interface CasoUsoAbrirTicketsPendientes {

    /** @return cuántas órdenes cubre el ticket abierto en esta pasada (0 = sin ticket). */
    int abrirTicketsPendientes();
}
