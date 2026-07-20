package com.ejemplo.app.business.ordermanager.aplicacion.servicio;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.ejemplo.app.testsoporte.ObservadorEjecucionEnMemoria;
import com.ejemplo.app.testsoporte.ObservadorEjecucionEnMemoria.Evento;
import com.ejemplo.app.testsoporte.RepositorioOrdenEnMemoria;
import com.ejemplo.app.business.ordermanager.dominio.ExternalId;
import com.ejemplo.app.business.ordermanager.dominio.OrdenId;
import com.ejemplo.app.business.ordermanager.dominio.OrdenRoot;
import com.ejemplo.app.business.ordermanager.dominio.ProcesoFalso;

/** Limpieza de datos: purga las órdenes finalizadas antiguas y devuelve el recuento. */
class ServicioLimpiezaDatosTest {

    private RepositorioOrdenEnMemoria repo;
    private ObservadorEjecucionEnMemoria observador;
    private ServicioLimpiezaDatos servicio;

    @BeforeEach
    void init() {
        repo = new RepositorioOrdenEnMemoria();
        observador = new ObservadorEjecucionEnMemoria();
        servicio = new ServicioLimpiezaDatos(repo, observador);
    }

    private void crearOrdenFinalizada() {
        var id = OrdenId.nuevo();
        var proceso = ProcesoFalso.crear(id, ExternalId.de(UUID.randomUUID().toString()));
        var orden = OrdenRoot.nueva(proceso, Instant.parse("2020-01-01T00:00:00Z"));
        orden.finalizar(Instant.now());
        repo.crear(orden);
    }

    @Test
    void purgarAnterioresA_devuelveElRecuentoDeOrdenesBorradas() {
        crearOrdenFinalizada();
        crearOrdenFinalizada();
        var corte = Instant.parse("2026-01-01T00:00:00Z");

        var resultado = servicio.purgarAnterioresA(corte);

        assertThat(resultado.ordenes()).isEqualTo(2);
        assertThat(observador.eventos()).containsExactly(new Evento.DatosAntiguosPurgados(2));
    }
}
