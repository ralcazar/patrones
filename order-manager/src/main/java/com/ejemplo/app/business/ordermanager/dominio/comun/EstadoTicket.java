package com.ejemplo.app.business.ordermanager.dominio.comun;

/**
 * Marcador de ticket de soporte a nivel de saga. Los fallos no abren tickets
 * en línea: dejan el flag PENDIENTE y el planificador de tickets (cada 3h,
 * de 8 a 17) abre UN ticket que cubre todas las sagas pendientes y las pasa a
 * ABIERTO con la fecha de apertura. No se guarda ningún id de ticket: abrir
 * un ticket es escribir un texto en el log y su id no se conoce.
 */
public enum EstadoTicket {
    /** Sin problemas que contar a soporte (o el reintento acabó bien y borró el flag). */
    SIN_TICKET,
    /** "Abrir ticket pendiente": el próximo barrido del planificador lo incluirá en el ticket. */
    PENDIENTE,
    /** El planificador ya avisó a soporte; la fecha queda en Saga.ticketAbiertoEn. */
    ABIERTO
}
