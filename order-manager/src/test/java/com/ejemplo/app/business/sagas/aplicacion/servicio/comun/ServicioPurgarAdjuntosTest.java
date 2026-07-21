package com.ejemplo.app.business.sagas.aplicacion.servicio.comun;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
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
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import com.ejemplo.app.business.ordermanager.aplicacion.puerto.salida.PuertoIncidencias;
import com.ejemplo.app.business.ordermanager.aplicacion.puerto.salida.RepositorioOrden;
import com.ejemplo.app.business.ordermanager.dominio.ExternalId;
import com.ejemplo.app.business.sagas.aplicacion.puerto.salida.RepositorioDatosNegocio;
import com.ejemplo.app.business.sagas.dominio.datosnegocio.DatosNegocioId;

/**
 * Selección por corte (30 días), idempotencia (la selección por lote ya
 * excluye lo purgado en una pasada anterior) y el camino de reintento +
 * incidencia (todas las ramas: éxito al primer intento, éxito tras fallo, y
 * agotar reintentos).
 */
class ServicioPurgarAdjuntosTest {

    private static final Instant AHORA = Instant.parse("2026-07-21T10:00:00Z");
    private static final Clock RELOJ = Clock.fixed(AHORA, ZoneOffset.UTC);

    private RepositorioOrden motor;
    private RepositorioDatosNegocio repoDatos;
    private PuertoIncidencias incidencias;
    private ServicioPurgarAdjuntos servicio;

    @BeforeEach
    void init() {
        motor = mock(RepositorioOrden.class);
        repoDatos = mock(RepositorioDatosNegocio.class);
        incidencias = mock(PuertoIncidencias.class);
        servicio = new ServicioPurgarAdjuntos(motor, repoDatos, incidencias, RELOJ);
        when(motor.externalIdsFinalizadosAntesDe(any())).thenReturn(List.of());
    }

    @Test
    void ejecutar_calculaElCorteComoAhoraMenos30Dias() {
        var corteCapturado = ArgumentCaptor.forClass(Instant.class);

        servicio.ejecutar();

        verify(motor).externalIdsFinalizadosAntesDe(corteCapturado.capture());
        assertThat(corteCapturado.getValue()).isEqualTo(AHORA.minus(Duration.ofDays(30)));
    }

    @Test
    void ejecutar_sinExternalIdsFinalizados_noConsultaNiTocaDatosNegocio() {
        servicio.ejecutar();

        verifyNoInteractions(repoDatos);
    }

    @Test
    void ejecutar_conExternalIdsFinalizados_purgaSoloLosDatosNegocioSinPurgarDelLote() {
        var externalId1 = ExternalId.de(UUID.randomUUID().toString());
        var externalId2 = ExternalId.de(UUID.randomUUID().toString());
        when(motor.externalIdsFinalizadosAntesDe(any())).thenReturn(List.of(externalId1, externalId2));
        var idSinPurgar1 = DatosNegocioId.nuevo();
        var idSinPurgar2 = DatosNegocioId.nuevo();
        when(repoDatos.idsPorExternalIdsSinPurgar(List.of(externalId1, externalId2)))
                .thenReturn(List.of(idSinPurgar1, idSinPurgar2));

        servicio.ejecutar();

        verify(repoDatos).purgarAdjuntos(idSinPurgar1);
        verify(repoDatos).purgarAdjuntos(idSinPurgar2);
    }

    @Test
    void ejecutar_segundaPasadaSinDatosNegocioSinPurgar_noPurgaNada() {
        var externalId = ExternalId.de(UUID.randomUUID().toString());
        when(motor.externalIdsFinalizadosAntesDe(any())).thenReturn(List.of(externalId));
        when(repoDatos.idsPorExternalIdsSinPurgar(anyList())).thenReturn(List.of()); // ya purgado antes

        servicio.ejecutar();

        verify(repoDatos, never()).purgarAdjuntos(any());
    }

    @Test
    void purgarAdjuntos_exitoAlPrimerIntento_noAbreIncidencia() {
        servicio.purgarAdjuntos();

        verifyNoInteractions(incidencias);
    }

    @Test
    void purgarAdjuntos_exitoTrasUnFallo_reintentaYNoAbreIncidencia() {
        var proxy = mock(ServicioPurgarAdjuntos.class);
        servicio.establecerSelf(proxy);
        var fallo = new RuntimeException("fallo transitorio");
        org.mockito.Mockito.doThrow(fallo).doNothing().when(proxy).ejecutar();

        servicio.purgarAdjuntos();

        verify(proxy, times(2)).ejecutar();
        verifyNoInteractions(incidencias);
    }

    @Test
    void purgarAdjuntos_agotaLosReintentosYAbreIncidenciaConLaCausaDelUltimoFallo() {
        var proxy = mock(ServicioPurgarAdjuntos.class);
        servicio.establecerSelf(proxy);
        org.mockito.Mockito.doThrow(
                new RuntimeException("boom1"), new RuntimeException("boom2"), new RuntimeException("boom3"),
                new RuntimeException("boom4"), new RuntimeException("boom5"))
                .when(proxy).ejecutar();

        servicio.purgarAdjuntos();

        verify(proxy, times(5)).ejecutar();
        verify(incidencias).abrir(eq("purga-adjuntos"), eq("RuntimeException: boom5"), eq(5));
    }

    @Test
    void establecerSelf_sustituyeElProxyUsadoParaLaAutoInvocacionTransaccional() {
        var proxy = mock(ServicioPurgarAdjuntos.class);
        servicio.establecerSelf(proxy);

        servicio.purgarAdjuntos();

        verify(proxy).ejecutar();
    }
}
