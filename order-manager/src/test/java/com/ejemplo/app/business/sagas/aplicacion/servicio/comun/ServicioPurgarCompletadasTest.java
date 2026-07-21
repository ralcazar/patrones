package com.ejemplo.app.business.sagas.aplicacion.servicio.comun;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.ejemplo.app.business.ordermanager.aplicacion.puerto.salida.PuertoIncidencias;
import com.ejemplo.app.business.ordermanager.aplicacion.puerto.salida.RepositorioOrden;
import com.ejemplo.app.business.ordermanager.dominio.ExternalId;
import com.ejemplo.app.business.sagas.aplicacion.puerto.salida.RepositorioDatosNegocio;
import com.ejemplo.app.business.sagas.dominio.datosnegocio.DatoNegocio1;
import com.ejemplo.app.business.sagas.dominio.datosnegocio.DatoNegocio2;
import com.ejemplo.app.business.sagas.dominio.datosnegocio.DatoNegocio3;
import com.ejemplo.app.business.sagas.dominio.datosnegocio.DatosNegocio;
import com.ejemplo.app.business.sagas.dominio.datosnegocio.DatosNegocioId;

/**
 * Selección por el corte recibido (lo calcula el planificador de
 * infraestructura, ver PlanificadorPurgaCompletadasTest), orden de borrado
 * (órdenes -- que incluyen la satélite con FK a datos_negocio -- ANTES que
 * datos_negocio, ver la javadoc de ServicioPurgarCompletadas), idempotencia
 * (borrar un datos_negocio ya borrado no falla), el recuento devuelto y el
 * camino de reintento + incidencia.
 */
class ServicioPurgarCompletadasTest {

    private static final Instant CORTE = Instant.parse("2026-01-22T10:00:00Z");

    private RepositorioOrden motor;
    private RepositorioDatosNegocio repoDatos;
    private PuertoIncidencias incidencias;
    private ServicioPurgarCompletadas servicio;

    @BeforeEach
    void init() {
        motor = mock(RepositorioOrden.class);
        repoDatos = mock(RepositorioDatosNegocio.class);
        incidencias = mock(PuertoIncidencias.class);
        servicio = new ServicioPurgarCompletadas(motor, repoDatos, incidencias);
        when(motor.externalIdsFinalizadosAntesDe(any())).thenReturn(List.of());
    }

    private static DatosNegocio datosNegocio(DatosNegocioId id, ExternalId externalId) {
        return DatosNegocio.crear(id, externalId, new DatoNegocio1(1),
                new DatoNegocio2(LocalDate.of(2026, 1, 1)), new DatoNegocio3("dato"));
    }

    @Test
    void ejecutar_consultaConElCorteRecibidoSinCalcularlo() {
        servicio.ejecutar(CORTE);

        verify(motor).externalIdsFinalizadosAntesDe(CORTE);
    }

    @Test
    void ejecutar_sinExternalIdsFinalizados_noBorraNadaYDevuelveCero() {
        var tocadas = servicio.ejecutar(CORTE);

        verify(motor, never()).purgarPorExternalIds(any());
        verifyNoInteractions(repoDatos);
        assertThat(tocadas).isZero();
    }

    @Test
    void ejecutar_conExternalIdsFinalizados_borraLasOrdenesAntesQueLosDatosDeNegocioYDevuelveElRecuento() {
        var externalId = ExternalId.de(UUID.randomUUID().toString());
        when(motor.externalIdsFinalizadosAntesDe(CORTE)).thenReturn(List.of(externalId));
        var id = DatosNegocioId.nuevo();
        when(repoDatos.buscarPorExternalId(externalId)).thenReturn(Optional.of(datosNegocio(id, externalId)));

        var tocadas = servicio.ejecutar(CORTE);

        var orden = inOrder(motor, repoDatos);
        orden.verify(motor).purgarPorExternalIds(List.of(externalId));
        orden.verify(repoDatos).borrar(id);
        assertThat(tocadas).isEqualTo(1L);
    }

    @Test
    void ejecutar_datosNegocioYaBorrado_noFallaNoInvocaBorrarYNoLoCuenta() {
        var externalId = ExternalId.de(UUID.randomUUID().toString());
        when(motor.externalIdsFinalizadosAntesDe(CORTE)).thenReturn(List.of(externalId));
        when(repoDatos.buscarPorExternalId(externalId)).thenReturn(Optional.empty()); // ya purgado antes

        var tocadas = servicio.ejecutar(CORTE);

        verify(repoDatos, never()).borrar(any());
        assertThat(tocadas).isZero();
    }

    @Test
    void purgarCompletadas_exitoAlPrimerIntento_noAbreIncidencia() {
        servicio.purgarCompletadas(CORTE);

        verifyNoInteractions(incidencias);
    }

    @Test
    void purgarCompletadas_exitoTrasUnFallo_reintentaYNoAbreIncidenciaYDevuelveElRecuento() {
        var proxy = mock(ServicioPurgarCompletadas.class);
        servicio.establecerSelf(proxy);
        var fallo = new RuntimeException("fallo transitorio");
        when(proxy.ejecutar(CORTE)).thenThrow(fallo).thenReturn(4L);

        var tocadas = servicio.purgarCompletadas(CORTE);

        verify(proxy, times(2)).ejecutar(CORTE);
        verifyNoInteractions(incidencias);
        assertThat(tocadas).isEqualTo(4L);
    }

    @Test
    void purgarCompletadas_agotaLosReintentosYAbreIncidenciaConLaCausaDelUltimoFalloYDevuelveCero() {
        var proxy = mock(ServicioPurgarCompletadas.class);
        servicio.establecerSelf(proxy);
        when(proxy.ejecutar(CORTE)).thenThrow(
                new RuntimeException("boom1"), new RuntimeException("boom2"), new RuntimeException("boom3"),
                new RuntimeException("boom4"), new RuntimeException("boom5"));

        var tocadas = servicio.purgarCompletadas(CORTE);

        verify(proxy, times(5)).ejecutar(CORTE);
        verify(incidencias).abrir(eq("purga-completadas"), eq("RuntimeException: boom5"), eq(5));
        assertThat(tocadas).isZero();
    }

    @Test
    void establecerSelf_sustituyeElProxyUsadoParaLaAutoInvocacionTransaccional() {
        var proxy = mock(ServicioPurgarCompletadas.class);
        servicio.establecerSelf(proxy);
        when(proxy.ejecutar(CORTE)).thenReturn(0L);

        servicio.purgarCompletadas(CORTE);

        verify(proxy).ejecutar(CORTE);
    }
}
