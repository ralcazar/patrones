package com.ejemplo.app.business.ordermanager.aplicacion.servicio.comun;

import java.time.Instant;

import jakarta.transaction.Transactional;

import org.jmolecules.ddd.annotation.Service;

import com.ejemplo.app.business.ordermanager.aplicacion.puerto.entrada.CasoUsoAbrirTicketsPendientes;
import com.ejemplo.app.business.ordermanager.aplicacion.puerto.salida.PuertoSagasTicketPendiente;
import com.ejemplo.app.business.ordermanager.aplicacion.puerto.salida.PuertoTicketsSoporte;
import com.ejemplo.app.business.ordermanager.aplicacion.puerto.salida.RepositorioOrden;
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
 *
 * El marcado de cada orden va en {@code @Transactional}. Como este servicio
 * es un POJO creado por {@code @Bean}, se invoca a través de {@code self}
 * (el propio proxy, inyectado por ConfiguracionOrderManager) para que la
 * anotación no se ignore por auto-invocación.
 */
@Service
public class ServicioTicketsSoporte implements CasoUsoAbrirTicketsPendientes {

    private final PuertoSagasTicketPendiente pendientes;
    private final PuertoTicketsSoporte tickets;
    private final RepositorioOrden repo;
    private ServicioTicketsSoporte self;

    public ServicioTicketsSoporte(PuertoSagasTicketPendiente pendientes, PuertoTicketsSoporte tickets,
            RepositorioOrden repo) {
        this.pendientes = pendientes;
        this.tickets = tickets;
        this.repo = repo;
        this.self = this; // valor por defecto (tests unitarios); ConfiguracionOrderManager lo sustituye por el proxy
    }

    /** Referencia al proxy transaccional de Spring de este mismo bean (ver ConfiguracionOrderManager). */
    public void establecerSelf(ServicioTicketsSoporte self) {
        this.self = self;
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
        ReintentoOptimista.ejecutar(() -> {
            self.aplicarMarcarAbierto(sagaId, apertura); // via proxy -> @Transactional
            return null;
        });
    }

    @Transactional
    public void aplicarMarcarAbierto(SagaId sagaId, Instant apertura) {
        var orden = repo.cargar(sagaId);
        orden.marcarTicketAbierto(apertura);
        repo.guardar(orden);
    }
}
