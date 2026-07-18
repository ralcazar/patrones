package com.ejemplo.app.business.ordermanager.aplicacion.servicio;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.ejemplo.app.business.ordermanager.aplicacion.puerto.salida.PuertoMensajesProcesados;
import com.ejemplo.app.testsoporte.ObservadorEjecucionEnMemoria;
import com.ejemplo.app.testsoporte.ObservadorEjecucionEnMemoria.Evento;
import com.ejemplo.app.testsoporte.RepositorioOrdenEnMemoria;
import com.ejemplo.app.business.ordermanager.dominio.ExternalId;
import com.ejemplo.app.business.ordermanager.dominio.OrdenId;
import com.ejemplo.app.business.ordermanager.dominio.OrdenRoot;
import com.ejemplo.app.business.ordermanager.dominio.ProcesoFalso;

/**
 * Limpieza de datos: purga en una única llamada las órdenes finalizadas
 * antiguas y los registros de deduplicación de mensajes, y suma ambos
 * recuentos en el resultado.
 */
class ServicioLimpiezaDatosTest {

    private RepositorioOrdenEnMemoria repo;
    private PuertoMensajesProcesados dedup;
    private ObservadorEjecucionEnMemoria observador;
    private ServicioLimpiezaDatos servicio;

    @BeforeEach
    void init() {
        repo = new RepositorioOrdenEnMemoria();
        dedup = mock(PuertoMensajesProcesados.class);
        observador = new ObservadorEjecucionEnMemoria();
        servicio = new ServicioLimpiezaDatos(repo, dedup, observador);
    }

    private void crearOrdenFinalizada() {
        var id = OrdenId.nuevo();
        var proceso = ProcesoFalso.crear(id, ExternalId.de(UUID.randomUUID().toString()));
        var orden = OrdenRoot.nueva(proceso, Instant.parse("2020-01-01T00:00:00Z"));
        orden.finalizar(Instant.now());
        repo.crear(orden);
    }

    @Test
    void purgarAnterioresA_sumaLoBorradoDeOrdenesYDeDeduplicacion() {
        crearOrdenFinalizada();
        crearOrdenFinalizada();
        var corte = Instant.parse("2026-01-01T00:00:00Z");
        when(dedup.purgarAnterioresA(corte)).thenReturn(5L);

        var resultado = servicio.purgarAnterioresA(corte);

        assertThat(resultado.ordenes()).isEqualTo(2);
        assertThat(resultado.mensajesDedup()).isEqualTo(5);
        assertThat(resultado.total()).isEqualTo(7);
        assertThat(observador.eventos()).containsExactly(new Evento.DatosAntiguosPurgados(2, 5));
    }
}
