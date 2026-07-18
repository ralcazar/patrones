package com.ejemplo.app.business.ordermanager.aplicacion.servicio;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.ejemplo.app.business.ordermanager.aplicacion.puerto.entrada.CasoUsoConsultarOrdenesSoporte.FiltroOrdenes;
import com.ejemplo.app.business.ordermanager.aplicacion.puerto.entrada.CasoUsoConsultarOrdenesSoporte.OrdenDetalle;
import com.ejemplo.app.business.ordermanager.aplicacion.puerto.entrada.CasoUsoConsultarOrdenesSoporte.OrdenResumen;
import com.ejemplo.app.business.ordermanager.aplicacion.puerto.entrada.CasoUsoConsultarOrdenesSoporte.PasoDetalle;
import com.ejemplo.app.business.ordermanager.aplicacion.puerto.salida.PuertoConsultaOrdenesSoporte;
import com.ejemplo.app.testsoporte.RepositorioOrdenEnMemoria;
import com.ejemplo.app.business.ordermanager.dominio.ExternalId;
import com.ejemplo.app.business.ordermanager.dominio.OrdenId;
import com.ejemplo.app.business.ordermanager.dominio.OrdenRoot;
import com.ejemplo.app.business.ordermanager.dominio.PoliticaReintentos;
import com.ejemplo.app.business.ordermanager.dominio.ProcesoFalso;
import com.ejemplo.app.business.ordermanager.dominio.UsuarioSoporte;

/**
 * Fachada de la pantalla de soporte: las intervenciones mutan el agregado a
 * través de RepositorioOrden (un único guardado); las consultas delegan tal
 * cual en el modelo de lectura, sin cargar agregados.
 */
class ServicioSoporteOrdenesTest {

    private static final Instant AHORA = Instant.parse("2026-01-01T00:00:00Z");

    private RepositorioOrdenEnMemoria repo;
    private PuertoConsultaOrdenesSoporte consultas;
    private ServicioSoporteOrdenes servicio;

    @BeforeEach
    void init() {
        repo = new RepositorioOrdenEnMemoria();
        consultas = mock(PuertoConsultaOrdenesSoporte.class);
        servicio = new ServicioSoporteOrdenes(repo, consultas);
    }

    private OrdenId crearOrden() {
        var id = OrdenId.nuevo();
        var proceso = ProcesoFalso.crear(id, ExternalId.de(UUID.randomUUID().toString()));
        repo.crear(OrdenRoot.nueva(proceso, AHORA));
        return id;
    }

    private static OrdenResumen resumenEjemplo(OrdenId id) {
        return new OrdenResumen(id, ProcesoFalso.TIPO, ExternalId.de(UUID.randomUUID().toString()),
                "INICIAL", 3, null, AHORA, AHORA, AHORA);
    }

    private static OrdenDetalle detalleEjemplo(OrdenId id) {
        return new OrdenDetalle(resumenEjemplo(id), true, List.of(new PasoDetalle("PASO1", true)), List.of());
    }

    @Test
    void reintentarPaso_reseteaLaEscaleraDeIntentosYDespiertaLaOrdenYa() {
        var id = crearOrden();
        var orden = repo.cargar(id);
        var politica = new PoliticaReintentos();
        for (int i = 0; i < 8; i++) {
            orden.programarReintento(politica, AHORA);
        }
        repo.guardar(orden);
        assertThat(repo.estadoActual(id).intentos()).isEqualTo(8);

        var antes = Instant.now();
        servicio.reintentarPaso(ProcesoFalso.TIPO, id, "PASO1", new UsuarioSoporte("ana"));
        var despues = Instant.now();

        var actualizada = repo.estadoActual(id);
        assertThat(actualizada.intentos()).isZero();
        assertThat(actualizada.proximoReintentoEn()).isBetween(antes, despues);
        assertThat(actualizada.tokenTrabajador()).isNull();
    }

    @Test
    void marcarPasoOk_aplicaLaMarcaManualEnElProcesoYDespiertaLaOrden() {
        var id = crearOrden();

        servicio.marcarPasoOk(ProcesoFalso.TIPO, id, "PASO1", new UsuarioSoporte("ana"),
                "arreglado a mano", Map.of("refInicio", "ABC"));

        var actualizada = repo.estadoActual(id);
        assertThat(actualizada.proceso().estado()).isEqualTo(ProcesoFalso.Estado.TERMINADO);
        assertThat(actualizada.tokenTrabajador()).isNull();
    }

    @Test
    void ordenesBloqueadas_delegaEnElModeloDeLectura() {
        var esperado = List.of(resumenEjemplo(crearOrden()));
        when(consultas.ordenesBloqueadas()).thenReturn(esperado);

        assertThat(servicio.ordenesBloqueadas()).isEqualTo(esperado);
    }

    @Test
    void ordenesEnEjecucion_delegaEnElModeloDeLectura() {
        var esperado = List.of(resumenEjemplo(crearOrden()));
        when(consultas.ordenesEnEjecucion()).thenReturn(esperado);

        assertThat(servicio.ordenesEnEjecucion()).isEqualTo(esperado);
    }

    @Test
    void ordenesConTicketPendiente_delegaEnElModeloDeLectura() {
        var esperado = List.of(resumenEjemplo(crearOrden()));
        when(consultas.ordenesConTicketPendiente()).thenReturn(esperado);

        assertThat(servicio.ordenesConTicketPendiente()).isEqualTo(esperado);
    }

    @Test
    void buscar_delegaElFiltroTalCualEnElModeloDeLectura() {
        var esperado = List.of(resumenEjemplo(crearOrden()));
        var filtroPorEstado = FiltroOrdenes.porEstado("INICIAL");
        when(consultas.buscar(filtroPorEstado)).thenReturn(esperado);

        assertThat(servicio.buscar(filtroPorEstado)).isEqualTo(esperado);
        verify(consultas).buscar(filtroPorEstado);
    }

    @Test
    void filtroOrdenes_iniciadaEntreYActualizadaEntreFijanSoloSusPropiosCriterios() {
        var desde = Instant.parse("2026-01-01T00:00:00Z");
        var hasta = Instant.parse("2026-01-31T00:00:00Z");

        var porInicio = FiltroOrdenes.iniciadaEntre(desde, hasta);
        assertThat(porInicio.iniciadaDesde()).isEqualTo(desde);
        assertThat(porInicio.iniciadaHasta()).isEqualTo(hasta);
        assertThat(porInicio.estado()).isNull();
        assertThat(porInicio.actualizadaDesde()).isNull();
        assertThat(porInicio.actualizadaHasta()).isNull();

        var porActualizacion = FiltroOrdenes.actualizadaEntre(desde, hasta);
        assertThat(porActualizacion.actualizadaDesde()).isEqualTo(desde);
        assertThat(porActualizacion.actualizadaHasta()).isEqualTo(hasta);
        assertThat(porActualizacion.estado()).isNull();
        assertThat(porActualizacion.iniciadaDesde()).isNull();
    }

    @Test
    void detalle_delegaEnElModeloDeLecturaConTipoEId() {
        var id = crearOrden();
        var esperado = detalleEjemplo(id);
        when(consultas.detalle(ProcesoFalso.TIPO, id)).thenReturn(esperado);

        var detalle = servicio.detalle(ProcesoFalso.TIPO, id);

        assertThat(detalle).isEqualTo(esperado);
        assertThat(detalle.cancelable()).isTrue();
        assertThat(detalle.pasos()).extracting(PasoDetalle::nombrePaso).containsExactly("PASO1");
        assertThat(detalle.auditoria()).isEmpty();
    }
}
