package com.ejemplo.app.business.ordermanager.aplicacion.servicio;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.ejemplo.app.business.ordermanager.aplicacion.puerto.salida.PuertoSagasTicketPendiente;
import com.ejemplo.app.business.ordermanager.aplicacion.puerto.salida.PuertoSagasTicketPendiente.SagaTicketPendiente;
import com.ejemplo.app.business.ordermanager.aplicacion.puerto.salida.PuertoTicketsSoporte;
import com.ejemplo.app.business.ordermanager.aplicacion.servicio.soporte.RepositorioOrdenEnMemoria;
import com.ejemplo.app.business.ordermanager.aplicacion.servicio.soporte.UnidadDeTrabajoInmediata;
import com.ejemplo.app.business.ordermanager.dominio.comun.ExternalId;
import com.ejemplo.app.business.ordermanager.dominio.comun.OrdenRoot;
import com.ejemplo.app.business.ordermanager.dominio.comun.PoliticaReintentos;
import com.ejemplo.app.business.ordermanager.dominio.comun.SagaId;
import com.ejemplo.app.business.ordermanager.dominio.comun.TipoSaga;
import com.ejemplo.app.business.ordermanager.dominio.sagaprincipal.DatoNegocio2;
import com.ejemplo.app.business.ordermanager.dominio.sagaprincipal.DatoNegocio3;
import com.ejemplo.app.business.ordermanager.dominio.sagaprincipal.SagaPrincipalRoot;

/**
 * Barrido de tickets: la marca es operativa (intentos &gt;= 8 sin
 * ticket_abierto_en), no de negocio. Verifica que no duplica el aviso
 * mientras dura el atasco y que abre un ticket NUEVO si la orden se recupera
 * y vuelve a atascarse (Fase 4 del refactor).
 */
class ServicioTicketsSoporteTest {

    private RepositorioOrdenEnMemoria repo;
    private UnidadDeTrabajoInmediata tx;
    private PuertoTicketsSoporte tickets;

    @BeforeEach
    void init() {
        repo = new RepositorioOrdenEnMemoria();
        tx = new UnidadDeTrabajoInmediata();
        tickets = mock(PuertoTicketsSoporte.class);
    }

    private SagaId crearOrdenAtascada(int intentos) {
        var id = SagaId.nuevo();
        var saga = SagaPrincipalRoot.crear(id, ExternalId.de(UUID.randomUUID().toString()),
                new DatoNegocio3("v1", "v2"), new DatoNegocio2("v1", "v2"));
        var orden = OrdenRoot.nueva(saga, Instant.now());
        var politica = new PoliticaReintentos();
        for (int i = 0; i < intentos; i++) {
            orden.programarReintento(politica, Instant.now());
        }
        repo.crear(orden);
        return id;
    }

    /** Puerto de lectura respaldado por el propio repo en memoria: misma condición que la query real. */
    private PuertoSagasTicketPendiente pendientesSobreElRepo() {
        return () -> repo.todas().stream()
                .filter(o -> o.intentos() >= 8 && o.ticketAbiertoEn() == null && o.estaViva())
                .map(o -> new SagaTicketPendiente(o.tipo(), o.sagaId(),
                        ((SagaPrincipalRoot) o.saga()).externalId(), o.intentos()))
                .toList();
    }

    @Test
    void abreUnTicketQueCubreTodasLasPendientesYLasMarca() {
        var id1 = crearOrdenAtascada(8);
        var id2 = crearOrdenAtascada(9);
        var servicio = new ServicioTicketsSoporte(pendientesSobreElRepo(), tickets, repo, tx);

        var cantidad = servicio.abrirTicketsPendientes();

        assertThat(cantidad).isEqualTo(2);
        verify(tickets, times(1)).abrir(any()); // UN solo ticket para las dos
        assertThat(repo.estadoActual(id1).ticketAbiertoEn()).isNotNull();
        assertThat(repo.estadoActual(id2).ticketAbiertoEn()).isNotNull();
    }

    @Test
    void unaSegundaPasadaMientrasDuraElAtasco_noDuplicaElAviso() {
        var id = crearOrdenAtascada(8);
        var servicio = new ServicioTicketsSoporte(pendientesSobreElRepo(), tickets, repo, tx);
        servicio.abrirTicketsPendientes();

        var cantidadSegundaPasada = servicio.abrirTicketsPendientes();

        assertThat(cantidadSegundaPasada).isZero();
        verify(tickets, times(1)).abrir(any()); // sigue siendo una única apertura
    }

    @Test
    void siLaOrdenSeRecuperaYVuelveAAtascarse_seAbreUnTicketNuevo() {
        var id = crearOrdenAtascada(8);
        var servicio = new ServicioTicketsSoporte(pendientesSobreElRepo(), tickets, repo, tx);
        servicio.abrirTicketsPendientes();
        assertThat(repo.estadoActual(id).ticketAbiertoEn()).isNotNull();

        // La orden se recupera: un paso OK resetea intentos y cierra la marca de ticket.
        var orden = repo.cargar(id);
        orden.resetearIntentos();
        repo.guardar(orden);
        assertThat(repo.estadoActual(id).ticketAbiertoEn()).isNull();

        // Vuelve a atascarse.
        var ordenAtascadaOtraVez = repo.cargar(id);
        var politica = new PoliticaReintentos();
        for (int i = 0; i < 8; i++) {
            ordenAtascadaOtraVez.programarReintento(politica, Instant.now());
        }
        repo.guardar(ordenAtascadaOtraVez);

        var cantidad = servicio.abrirTicketsPendientes();

        assertThat(cantidad).isEqualTo(1);
        verify(tickets, times(2)).abrir(any()); // segundo ticket, independiente del primero
        assertThat(repo.estadoActual(id).ticketAbiertoEn()).isNotNull();
    }
}
