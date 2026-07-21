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

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

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
 * Selección por corte (180 días), orden de borrado (órdenes -- que incluyen
 * la satélite con FK a datos_negocio -- ANTES que datos_negocio, ver la
 * javadoc de ServicioPurgarCompletadas), idempotencia (borrar un datos_negocio
 * ya borrado no falla) y el camino de reintento + incidencia.
 */
class ServicioPurgarCompletadasTest {

    private static final Instant AHORA = Instant.parse("2026-07-21T10:00:00Z");
    private static final Clock RELOJ = Clock.fixed(AHORA, ZoneOffset.UTC);

    private RepositorioOrden motor;
    private RepositorioDatosNegocio repoDatos;
    private PuertoIncidencias incidencias;
    private ServicioPurgarCompletadas servicio;

    @BeforeEach
    void init() {
        motor = mock(RepositorioOrden.class);
        repoDatos = mock(RepositorioDatosNegocio.class);
        incidencias = mock(PuertoIncidencias.class);
        servicio = new ServicioPurgarCompletadas(motor, repoDatos, incidencias, RELOJ);
        when(motor.externalIdsFinalizadosAntesDe(any())).thenReturn(List.of());
    }

    private static DatosNegocio datosNegocio(DatosNegocioId id, ExternalId externalId) {
        return DatosNegocio.crear(id, externalId, new DatoNegocio1(1),
                new DatoNegocio2(LocalDate.of(2026, 1, 1)), new DatoNegocio3("dato"));
    }

    @Test
    void ejecutar_calculaElCorteComoAhoraMenos180Dias() {
        var corteCapturado = ArgumentCaptor.forClass(Instant.class);

        servicio.ejecutar();

        verify(motor).externalIdsFinalizadosAntesDe(corteCapturado.capture());
        assertThat(corteCapturado.getValue()).isEqualTo(AHORA.minus(Duration.ofDays(180)));
    }

    @Test
    void ejecutar_sinExternalIdsFinalizados_noBorraNada() {
        servicio.ejecutar();

        verify(motor, never()).purgarPorExternalIds(any());
        verifyNoInteractions(repoDatos);
    }

    @Test
    void ejecutar_conExternalIdsFinalizados_borraLasOrdenesAntesQueLosDatosDeNegocio() {
        var externalId = ExternalId.de(UUID.randomUUID().toString());
        when(motor.externalIdsFinalizadosAntesDe(any())).thenReturn(List.of(externalId));
        var id = DatosNegocioId.nuevo();
        when(repoDatos.buscarPorExternalId(externalId)).thenReturn(Optional.of(datosNegocio(id, externalId)));

        servicio.ejecutar();

        var orden = inOrder(motor, repoDatos);
        orden.verify(motor).purgarPorExternalIds(List.of(externalId));
        orden.verify(repoDatos).borrar(id);
    }

    @Test
    void ejecutar_datosNegocioYaBorrado_noFallaYNoInvocaBorrar() {
        var externalId = ExternalId.de(UUID.randomUUID().toString());
        when(motor.externalIdsFinalizadosAntesDe(any())).thenReturn(List.of(externalId));
        when(repoDatos.buscarPorExternalId(externalId)).thenReturn(Optional.empty()); // ya purgado antes

        servicio.ejecutar();

        verify(repoDatos, never()).borrar(any());
    }

    @Test
    void purgarCompletadas_exitoAlPrimerIntento_noAbreIncidencia() {
        servicio.purgarCompletadas();

        verifyNoInteractions(incidencias);
    }

    @Test
    void purgarCompletadas_exitoTrasUnFallo_reintentaYNoAbreIncidencia() {
        var proxy = mock(ServicioPurgarCompletadas.class);
        servicio.establecerSelf(proxy);
        var fallo = new RuntimeException("fallo transitorio");
        org.mockito.Mockito.doThrow(fallo).doNothing().when(proxy).ejecutar();

        servicio.purgarCompletadas();

        verify(proxy, times(2)).ejecutar();
        verifyNoInteractions(incidencias);
    }

    @Test
    void purgarCompletadas_agotaLosReintentosYAbreIncidenciaConLaCausaDelUltimoFallo() {
        var proxy = mock(ServicioPurgarCompletadas.class);
        servicio.establecerSelf(proxy);
        org.mockito.Mockito.doThrow(
                new RuntimeException("boom1"), new RuntimeException("boom2"), new RuntimeException("boom3"),
                new RuntimeException("boom4"), new RuntimeException("boom5"))
                .when(proxy).ejecutar();

        servicio.purgarCompletadas();

        verify(proxy, times(5)).ejecutar();
        verify(incidencias).abrir(eq("purga-completadas"), eq("RuntimeException: boom5"), eq(5));
    }

    @Test
    void establecerSelf_sustituyeElProxyUsadoParaLaAutoInvocacionTransaccional() {
        var proxy = mock(ServicioPurgarCompletadas.class);
        servicio.establecerSelf(proxy);

        servicio.purgarCompletadas();

        verify(proxy).ejecutar();
    }
}
