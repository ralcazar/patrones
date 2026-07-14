package com.ejemplo.app.business.ordermanager.aplicacion.servicio;

import java.time.Instant;

import org.jmolecules.ddd.annotation.Service;

import com.ejemplo.app.business.ordermanager.aplicacion.puerto.entrada.CasoUsoAbrirTicketsPendientes;
import com.ejemplo.app.business.ordermanager.aplicacion.puerto.salida.PuertoSagasTicketPendiente;
import com.ejemplo.app.business.ordermanager.aplicacion.puerto.salida.PuertoSagasTicketPendiente.SagaTicketPendiente;
import com.ejemplo.app.business.ordermanager.aplicacion.puerto.salida.PuertoTicketsSoporte;

/**
 * Barrido de tickets: los fallos ya NO abren ticket en línea, solo dejan la
 * saga con el flag "abrir ticket pendiente". Este servicio, disparado por el
 * PlanificadorTicketsSoporte (cada 3h de 8 a 17), busca las sagas PENDIENTE,
 * abre UN único ticket que las cubre a todas (escribir en el log: sin id) y
 * las marca ABIERTO con la fecha de apertura.
 *
 * Sin duplicados: al quedar ABIERTO la saga desaparece de la query; solo
 * volvería a pedir ticket si el problema se cura (el flag se borra al
 * completar) y más adelante aparece un problema nuevo.
 *
 * Orden deliberado (I/O fuera de tx): primero el ticket, después marcar las
 * sagas. Si el proceso muere entre medias, el siguiente barrido repite el
 * aviso (at-least-once): mejor un ticket de más que un error invisible.
 */
@Service
public class ServicioTicketsSoporte implements CasoUsoAbrirTicketsPendientes {

    private final PuertoSagasTicketPendiente pendientes;
    private final PuertoTicketsSoporte tickets;
    private final ServicioSagaPrincipal principal;
    private final ServicioSagaSecundaria1 secundaria1;
    private final ServicioSagaSecundaria2 secundaria2;
    private final ServicioSagaSecundaria3 secundaria3;

    public ServicioTicketsSoporte(PuertoSagasTicketPendiente pendientes, PuertoTicketsSoporte tickets,
            ServicioSagaPrincipal principal, ServicioSagaSecundaria1 secundaria1,
            ServicioSagaSecundaria2 secundaria2, ServicioSagaSecundaria3 secundaria3) {
        this.pendientes = pendientes;
        this.tickets = tickets;
        this.principal = principal;
        this.secundaria1 = secundaria1;
        this.secundaria2 = secundaria2;
        this.secundaria3 = secundaria3;
    }

    @Override
    public int abrirTicketsPendientes() {
        var sagas = pendientes.buscar();
        if (sagas.isEmpty()) {
            return 0;
        }
        tickets.abrir(sagas); // un solo ticket para todas
        var apertura = Instant.now();
        for (var saga : sagas) {
            marcarAbierto(saga, apertura);
        }
        return sagas.size();
    }

    /** Enruta al orquestador correcto, que persiste ABIERTO + fecha en la saga. */
    private void marcarAbierto(SagaTicketPendiente saga, Instant apertura) {
        switch (saga.tipo()) {
            case PRINCIPAL -> principal.marcarTicketAbierto(saga.sagaId(), apertura);
            case SECUNDARIA1 -> secundaria1.marcarTicketAbierto(saga.sagaId(), apertura);
            case SECUNDARIA2 -> secundaria2.marcarTicketAbierto(saga.sagaId(), apertura);
            case SECUNDARIA3 -> secundaria3.marcarTicketAbierto(saga.sagaId(), apertura);
        }
    }
}
