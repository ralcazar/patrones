package com.ejemplo.app.infraestructure.ordermanager.saga;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import com.ejemplo.app.business.ordermanager.aplicacion.puerto.entrada.CasoUsoAbrirTicketsPendientes;

/**
 * Disparador periódico de la apertura de tickets: cada 3 horas en horario de
 * soporte (8, 11, 14 y 17h) barre las sagas con "abrir ticket pendiente" y
 * abre UN ticket que las cubre a todas. El QUÉ (query, ticket único, marcar
 * ABIERTO con fecha) vive en la aplicación (ServicioTicketsSoporte); aquí
 * solo el CUÁNDO.
 */
@Component
public class PlanificadorTicketsSoporte {

    private static final Logger log = LoggerFactory.getLogger(PlanificadorTicketsSoporte.class);

    private final CasoUsoAbrirTicketsPendientes tickets;

    public PlanificadorTicketsSoporte(CasoUsoAbrirTicketsPendientes tickets) {
        this.tickets = tickets;
    }

    @Scheduled(cron = "${ordermanager.tickets.cron:0 0 8-17/3 * * *}")
    public void ejecutar() {
        int sagas = tickets.abrirTicketsPendientes();
        if (sagas > 0) {
            log.info("Barrido de tickets: abierto 1 ticket que cubre {} sagas", sagas);
        }
    }
}
