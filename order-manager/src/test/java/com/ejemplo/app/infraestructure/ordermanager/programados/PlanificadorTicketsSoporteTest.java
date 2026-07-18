package com.ejemplo.app.infraestructure.ordermanager.programados;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.Test;

import com.ejemplo.app.business.ordermanager.aplicacion.puerto.entrada.CasoUsoAbrirTicketsPendientes;

/** Dispara el barrido de tickets; el log solo se escribe si hubo alguna orden cubierta. */
class PlanificadorTicketsSoporteTest {

    @Test
    void ejecutar_conOrdenesCubiertasInvocaElCasoDeUso() {
        var tickets = mock(CasoUsoAbrirTicketsPendientes.class);
        when(tickets.abrirTicketsPendientes()).thenReturn(4);
        var planificador = new PlanificadorTicketsSoporte(tickets);

        planificador.ejecutar();

        verify(tickets).abrirTicketsPendientes();
    }

    @Test
    void ejecutar_sinOrdenesCubiertasNoRompe() {
        var tickets = mock(CasoUsoAbrirTicketsPendientes.class);
        when(tickets.abrirTicketsPendientes()).thenReturn(0);
        var planificador = new PlanificadorTicketsSoporte(tickets);

        planificador.ejecutar();

        verify(tickets).abrirTicketsPendientes();
    }
}
