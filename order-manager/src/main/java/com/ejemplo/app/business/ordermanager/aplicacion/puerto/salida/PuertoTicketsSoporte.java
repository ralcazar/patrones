package com.ejemplo.app.business.ordermanager.aplicacion.puerto.salida;

import java.util.List;

import com.ejemplo.app.business.ordermanager.aplicacion.puerto.salida.PuertoSagasTicketPendiente.SagaTicketPendiente;

/**
 * Apertura del ticket al equipo de soporte: UN único ticket que cubre todas
 * las sagas pendientes del barrido. Abrir un ticket es escribir un cierto
 * texto en el log: no devuelve ningún id porque no se conoce (las sagas solo
 * guardan que su ticket está ABIERTO y desde cuándo).
 */
public interface PuertoTicketsSoporte {

    void abrir(List<SagaTicketPendiente> sagas);
}
