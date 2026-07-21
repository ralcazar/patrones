package com.ejemplo.app.business.ordermanager.aplicacion.servicio;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

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

    private static final Instant CORTE = Instant.parse("2026-01-01T00:00:00Z");

    private RepositorioOrdenEnMemoria repo;
    private ObservadorEjecucionEnMemoria observador;
    private ServicioLimpiezaDatos servicio;

    @BeforeEach
    void init() {
        repo = new RepositorioOrdenEnMemoria();
        observador = new ObservadorEjecucionEnMemoria();
        servicio = new ServicioLimpiezaDatos(repo, observador);
    }

    private OrdenId crearOrdenFinalizadaEn(Instant completadaEn) {
        var id = OrdenId.nuevo();
        var proceso = ProcesoFalso.crear(id, ExternalId.de(UUID.randomUUID().toString()));
        var orden = OrdenRoot.nueva(proceso, completadaEn.minusSeconds(1));
        orden.finalizar(completadaEn);
        repo.crear(orden);
        return id;
    }

    private OrdenId crearOrdenViva() {
        var id = OrdenId.nuevo();
        var proceso = ProcesoFalso.crear(id, ExternalId.de(UUID.randomUUID().toString()));
        repo.crear(OrdenRoot.nueva(proceso, CORTE.minusSeconds(3600))); // candidata vieja pero NUNCA finalizada
        return id;
    }

    @Test
    void purgarAnterioresA_borraSoloLasFinalizadasAntesDelCorteYDevuelveElRecuento() {
        var idVieja1 = crearOrdenFinalizadaEn(CORTE.minusSeconds(3600));
        var idVieja2 = crearOrdenFinalizadaEn(CORTE.minusSeconds(10 * 24 * 3600));
        var idReciente = crearOrdenFinalizadaEn(CORTE.plusSeconds(1)); // finalizada TRAS el corte: sobrevive
        var idViva = crearOrdenViva(); // nunca finalizada: la purga no la toca, sea cual sea el corte

        var resultado = servicio.purgarAnterioresA(CORTE);

        assertThat(resultado.ordenes()).isEqualTo(2);
        assertThat(observador.eventos()).containsExactly(new Evento.DatosAntiguosPurgados(2));
        // Las dos anteriores al corte desaparecen...
        assertThatThrownBy(() -> repo.cargar(idVieja1)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> repo.cargar(idVieja2)).isInstanceOf(IllegalArgumentException.class);
        // ...y la finalizada tras el corte y la que sigue viva permanecen.
        assertThat(repo.cargar(idReciente)).isNotNull();
        assertThat(repo.cargar(idViva)).isNotNull();
    }

    @Test
    void purgarAnterioresA_sinFinalizadasAntesDelCorte_noBorraNadaYDevuelveCero() {
        crearOrdenFinalizadaEn(CORTE.plusSeconds(1)); // finalizada tras el corte
        crearOrdenViva();

        var resultado = servicio.purgarAnterioresA(CORTE);

        assertThat(resultado.ordenes()).isZero();
        assertThat(observador.eventos()).containsExactly(new Evento.DatosAntiguosPurgados(0));
    }
}
