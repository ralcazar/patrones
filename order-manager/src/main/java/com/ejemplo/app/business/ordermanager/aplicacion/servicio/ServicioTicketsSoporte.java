package com.ejemplo.app.business.ordermanager.aplicacion.servicio;

import java.time.Instant;

import org.jmolecules.ddd.annotation.Service;

import com.ejemplo.app.business.ordermanager.aplicacion.puerto.entrada.CasoUsoAbrirTicketsPendientes;
import com.ejemplo.app.business.ordermanager.aplicacion.puerto.salida.PuertoSagasTicketPendiente;
import com.ejemplo.app.business.ordermanager.aplicacion.puerto.salida.PuertoTicketsSoporte;
import com.ejemplo.app.business.ordermanager.aplicacion.puerto.salida.RepositorioOrden;
import com.ejemplo.app.business.ordermanager.aplicacion.puerto.salida.UnidadDeTrabajo;
import com.ejemplo.app.business.ordermanager.dominio.comun.SagaId;

/**
 * Barrido de tickets: la marca de "ticket pendiente" ya no es de negocio, es
 * operativa ({@code OrdenRoot.ticketAbiertoEn == null} con {@code intentos >= 8}).
 * Este servicio, disparado por el PlanificadorTicketsSoporte, busca las
 * órdenes pendientes, abre UN único ticket que las cubre a todas (escribir en
 * el log: sin id) y marca cada orden con la fecha de apertura.
 *
 * Orden deliberado (I/O fuera de tx): primero el ticket, después marcar las
 * órdenes. Si el proceso muere entre medias, el siguiente barrido repite el
 * aviso (at-least-once): mejor un ticket de más que un error invisible.
 */
@Service
public class ServicioTicketsSoporte implements CasoUsoAbrirTicketsPendientes {

    private final PuertoSagasTicketPendiente pendientes;
    private final PuertoTicketsSoporte tickets;
    private final RepositorioOrden repo;
    private final UnidadDeTrabajo tx;

    public ServicioTicketsSoporte(PuertoSagasTicketPendiente pendientes, PuertoTicketsSoporte tickets,
            RepositorioOrden repo, UnidadDeTrabajo tx) {
        this.pendientes = pendientes;
        this.tickets = tickets;
        this.repo = repo;
        this.tx = tx;
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
            marcarAbierto(saga.sagaId(), apertura);
        }
        return sagas.size();
    }

    /** Si conflicto optimista (poco probable: la orden estaba bloqueada, sin token), recarga y reintenta. */
    private void marcarAbierto(SagaId sagaId, Instant apertura) {
        ReintentoOptimista.ejecutar(() -> tx.enTransaccion(() -> {
            var orden = repo.cargar(sagaId);
            orden.marcarTicketAbierto(apertura);
            repo.guardar(orden);
            return null;
        }));
    }
}
