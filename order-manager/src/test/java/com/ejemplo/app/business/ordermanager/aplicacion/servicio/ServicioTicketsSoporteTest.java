package com.ejemplo.app.business.ordermanager.aplicacion.servicio;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.ejemplo.app.business.ordermanager.aplicacion.puerto.salida.PuertoOrdenesTicketPendiente;
import com.ejemplo.app.business.ordermanager.aplicacion.puerto.salida.PuertoOrdenesTicketPendiente.OrdenTicketPendiente;
import com.ejemplo.app.business.ordermanager.aplicacion.puerto.salida.PuertoTicketsSoporte;
import com.ejemplo.app.business.ordermanager.aplicacion.puerto.salida.RepositorioOrden;
import com.ejemplo.app.testsoporte.RepositorioOrdenEnMemoria;
import com.ejemplo.app.business.ordermanager.dominio.ConcurrenciaOptimistaException;
import com.ejemplo.app.business.ordermanager.dominio.DetalleError;
import com.ejemplo.app.business.ordermanager.dominio.ExternalId;
import com.ejemplo.app.business.ordermanager.dominio.OrdenRoot;
import com.ejemplo.app.business.ordermanager.dominio.PoliticaReintentos;
import com.ejemplo.app.business.ordermanager.dominio.OrdenId;
import com.ejemplo.app.business.ordermanager.dominio.ProcesoFalso;

/**
 * Barrido de tickets: la marca es operativa (intentos &gt;= 8 sin
 * ticket_abierto_en), no de negocio. Verifica que no duplica el aviso
 * mientras dura el atasco y que abre un ticket NUEVO si la orden se recupera
 * y vuelve a atascarse (Fase 4 del refactor).
 */
class ServicioTicketsSoporteTest {

    private RepositorioOrdenEnMemoria repo;
    private PuertoTicketsSoporte tickets;

    @BeforeEach
    void init() {
        repo = new RepositorioOrdenEnMemoria();
        tickets = mock(PuertoTicketsSoporte.class);
    }

    private OrdenId crearOrdenAtascada(int intentos) {
        var id = OrdenId.nuevo();
        var proceso = ProcesoFalso.crear(id, ExternalId.de(UUID.randomUUID().toString()));
        var orden = OrdenRoot.nueva(proceso, Instant.now());
        var politica = new PoliticaReintentos();
        for (int i = 0; i < intentos; i++) {
            orden.programarReintento(politica, new DetalleError("java.lang.RuntimeException", "boom"), Instant.now());
        }
        repo.crear(orden);
        return id;
    }

    /** Puerto de lectura respaldado por el propio repo en memoria: misma condición que la query real. */
    private PuertoOrdenesTicketPendiente pendientesSobreElRepo() {
        return () -> repo.todas().stream()
                .filter(o -> o.intentos() >= 8 && o.ticketAbiertoEn() == null && o.estaViva())
                .map(o -> new OrdenTicketPendiente(o.tipo(), o.id(),
                        o.proceso().externalId(), o.intentos(), o.ultimoError()))
                .toList();
    }

    @Test
    void abreUnTicketQueCubreTodasLasPendientesYLasMarca() {
        var id1 = crearOrdenAtascada(8);
        var id2 = crearOrdenAtascada(9);
        var servicio = new ServicioTicketsSoporte(pendientesSobreElRepo(), tickets, repo);

        var cantidad = servicio.abrirTicketsPendientes();

        assertThat(cantidad).isEqualTo(2);
        verify(tickets, times(1)).abrir(any()); // UN solo ticket para las dos
        assertThat(repo.estadoActual(id1).ticketAbiertoEn()).isNotNull();
        assertThat(repo.estadoActual(id2).ticketAbiertoEn()).isNotNull();
    }

    @Test
    void unaSegundaPasadaMientrasDuraElAtasco_noDuplicaElAviso() {
        var id = crearOrdenAtascada(8);
        var servicio = new ServicioTicketsSoporte(pendientesSobreElRepo(), tickets, repo);
        servicio.abrirTicketsPendientes();

        var cantidadSegundaPasada = servicio.abrirTicketsPendientes();

        assertThat(cantidadSegundaPasada).isZero();
        verify(tickets, times(1)).abrir(any()); // sigue siendo una única apertura
    }

    @Test
    void siLaOrdenSeRecuperaYVuelveAAtascarse_seAbreUnTicketNuevo() {
        var id = crearOrdenAtascada(8);
        var servicio = new ServicioTicketsSoporte(pendientesSobreElRepo(), tickets, repo);
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
            ordenAtascadaOtraVez.programarReintento(politica, new DetalleError("java.lang.RuntimeException", "boom"), Instant.now());
        }
        repo.guardar(ordenAtascadaOtraVez);

        var cantidad = servicio.abrirTicketsPendientes();

        assertThat(cantidad).isEqualTo(1);
        verify(tickets, times(2)).abrir(any()); // segundo ticket, independiente del primero
        assertThat(repo.estadoActual(id).ticketAbiertoEn()).isNotNull();
    }

    @Test
    void aplicarMarcarAbierto_conConflictoTransitorio_reintentaYAcabaMarcandoLaOrden() {
        var id = crearOrdenAtascada(8);
        var repoConFallo = new RepositorioConFalloDeGuardado(repo, id, 2); // falla 2 veces, gana al 3er intento
        var servicio = new ServicioTicketsSoporte(pendientesSobreElRepo(), tickets, repoConFallo);

        var cantidad = servicio.abrirTicketsPendientes();

        assertThat(cantidad).isEqualTo(1);
        assertThat(repo.estadoActual(id).ticketAbiertoEn()).isNotNull();
    }

    @Test
    void aplicarMarcarAbierto_conConflictoPersistente_propagaTrasAgotarLosCincoReintentos() {
        var id = crearOrdenAtascada(8);
        var repoConFallo = new RepositorioConFalloDeGuardado(repo, id, Integer.MAX_VALUE); // nunca se resuelve
        var servicio = new ServicioTicketsSoporte(pendientesSobreElRepo(), tickets, repoConFallo);

        assertThatThrownBy(servicio::abrirTicketsPendientes).isInstanceOf(ConcurrenciaOptimistaException.class);
    }

    @Test
    void establecerSelf_sustituyeElProxyUsadoParaAplicarMarcarAbierto() {
        var id = crearOrdenAtascada(8);
        var servicio = new ServicioTicketsSoporte(pendientesSobreElRepo(), tickets, repo);
        var proxy = spy(servicio);
        servicio.establecerSelf(proxy);

        var cantidad = servicio.abrirTicketsPendientes();

        assertThat(cantidad).isEqualTo(1);
        verify(proxy).aplicarMarcarAbierto(eq(id), any());
    }

    /** Decorador que falla el {@code guardar} de UNA orden concreta las primeras N veces (simula conflicto optimista). */
    private static final class RepositorioConFalloDeGuardado implements RepositorioOrden {

        private final RepositorioOrdenEnMemoria delegado;
        private final OrdenId objetivo;
        private int fallosRestantes;

        RepositorioConFalloDeGuardado(RepositorioOrdenEnMemoria delegado, OrdenId objetivo, int fallos) {
            this.delegado = delegado;
            this.objetivo = objetivo;
            this.fallosRestantes = fallos;
        }

        @Override
        public void crear(OrdenRoot orden) { delegado.crear(orden); }

        @Override
        public OrdenRoot cargar(OrdenId id) { return delegado.cargar(id); }

        @Override
        public OrdenRoot guardar(OrdenRoot orden) {
            if (orden.id().equals(objetivo) && fallosRestantes > 0) {
                fallosRestantes--;
                throw new ConcurrenciaOptimistaException(orden.id(), orden.version());
            }
            return delegado.guardar(orden);
        }

        @Override
        public List<CandidataOrden> buscarEjecutables(Instant ahora, int limite) {
            return delegado.buscarEjecutables(ahora, limite);
        }

        @Override
        public boolean hayEjecutables(Instant ahora) { return delegado.hayEjecutables(ahora); }

        @Override
        public List<ExternalId> externalIdsFinalizadosAntesDe(Instant corte) {
            return delegado.externalIdsFinalizadosAntesDe(corte);
        }

        @Override
        public long purgarPorExternalIds(List<ExternalId> ids) { return delegado.purgarPorExternalIds(ids); }
    }
}
