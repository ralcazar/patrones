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

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
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
 * Selección por el corte recibido (lo calcula el planificador de
 * infraestructura, ver PlanificadorPurgaAdjuntosTest), idempotencia (la
 * selección por lote ya excluye lo purgado en una pasada anterior), el
 * recuento devuelto y el camino de reintento + incidencia (todas las ramas:
 * éxito al primer intento, éxito tras fallo, y agotar reintentos).
 */
class ServicioPurgarAdjuntosTest {

    private static final Instant CORTE = Instant.parse("2026-06-21T10:00:00Z");

    private RepositorioOrden motor;
    private RepositorioDatosNegocio repoDatos;
    private PuertoIncidencias incidencias;
    private ServicioPurgarAdjuntos servicio;

    @BeforeEach
    void init() {
        motor = mock(RepositorioOrden.class);
        repoDatos = mock(RepositorioDatosNegocio.class);
        incidencias = mock(PuertoIncidencias.class);
        servicio = new ServicioPurgarAdjuntos(motor, repoDatos, incidencias);
        when(motor.externalIdsFinalizadosAntesDe(any())).thenReturn(List.of());
    }

    private static DatosNegocio datosNegocioSinPurgar(DatosNegocioId id, ExternalId externalId) {
        return DatosNegocio.crear(id, externalId, new DatoNegocio1(1),
                new DatoNegocio2(LocalDate.of(2026, 1, 1)), new DatoNegocio3("dato"));
    }

    @Test
    void ejecutar_consultaConElCorteRecibidoSinCalcularlo() {
        servicio.ejecutar(CORTE);

        verify(motor).externalIdsFinalizadosAntesDe(CORTE);
    }

    @Test
    void ejecutar_sinExternalIdsFinalizados_noConsultaNiTocaDatosNegocioYDevuelveCero() {
        var tocadas = servicio.ejecutar(CORTE);

        verifyNoInteractions(repoDatos);
        assertThat(tocadas).isZero();
    }

    @Test
    void ejecutar_conExternalIdsFinalizados_purgaSoloLosDatosNegocioSinPurgarDelLoteYDevuelveElRecuento() {
        var externalId1 = ExternalId.de(UUID.randomUUID().toString());
        var externalId2 = ExternalId.de(UUID.randomUUID().toString());
        when(motor.externalIdsFinalizadosAntesDe(CORTE)).thenReturn(List.of(externalId1, externalId2));
        var idSinPurgar1 = DatosNegocioId.nuevo();
        var idSinPurgar2 = DatosNegocioId.nuevo();
        when(repoDatos.idsPorExternalIdsSinPurgar(List.of(externalId1, externalId2)))
                .thenReturn(List.of(idSinPurgar1, idSinPurgar2));
        when(repoDatos.cargar(idSinPurgar1)).thenReturn(datosNegocioSinPurgar(idSinPurgar1, externalId1));
        when(repoDatos.cargar(idSinPurgar2)).thenReturn(datosNegocioSinPurgar(idSinPurgar2, externalId2));
        var antes = Instant.now();

        var tocadas = servicio.ejecutar(CORTE);

        var despues = Instant.now();
        var captor1 = ArgumentCaptor.forClass(Instant.class);
        var captor2 = ArgumentCaptor.forClass(Instant.class);
        verify(repoDatos).purgarAdjuntos(eq(idSinPurgar1), captor1.capture());
        verify(repoDatos).purgarAdjuntos(eq(idSinPurgar2), captor2.capture());
        assertThat(captor1.getValue()).isBetween(antes, despues);
        assertThat(captor2.getValue()).isEqualTo(captor1.getValue()); // mismo ahora hoisteado para el lote
        assertThat(tocadas).isEqualTo(2L);
    }

    @Test
    void ejecutar_sellaElDominioAntesDeTransportarloAlAdaptador() {
        var externalId = ExternalId.de(UUID.randomUUID().toString());
        when(motor.externalIdsFinalizadosAntesDe(CORTE)).thenReturn(List.of(externalId));
        var idSinPurgar = DatosNegocioId.nuevo();
        when(repoDatos.idsPorExternalIdsSinPurgar(List.of(externalId))).thenReturn(List.of(idSinPurgar));
        var datosNegocio = datosNegocioSinPurgar(idSinPurgar, externalId);
        when(repoDatos.cargar(idSinPurgar)).thenReturn(datosNegocio);

        servicio.ejecutar(CORTE);

        assertThat(datosNegocio.estaPurgada()).isTrue();
        verify(repoDatos).purgarAdjuntos(idSinPurgar, datosNegocio.purgadoEn());
    }

    @Test
    void ejecutar_segundaPasadaSinDatosNegocioSinPurgar_noPurgaNadaYDevuelveCero() {
        var externalId = ExternalId.de(UUID.randomUUID().toString());
        when(motor.externalIdsFinalizadosAntesDe(CORTE)).thenReturn(List.of(externalId));
        when(repoDatos.idsPorExternalIdsSinPurgar(anyList())).thenReturn(List.of()); // ya purgado antes

        var tocadas = servicio.ejecutar(CORTE);

        verify(repoDatos, never()).cargar(any());
        verify(repoDatos, never()).purgarAdjuntos(any(), any());
        assertThat(tocadas).isZero();
    }

    @Test
    void purgarAdjuntos_exitoAlPrimerIntento_noAbreIncidencia() {
        servicio.purgarAdjuntos(CORTE);

        verifyNoInteractions(incidencias);
    }

    @Test
    void purgarAdjuntos_exitoTrasUnFallo_reintentaYNoAbreIncidenciaYDevuelveElRecuento() {
        var proxy = mock(ServicioPurgarAdjuntos.class);
        servicio.establecerSelf(proxy);
        var fallo = new RuntimeException("fallo transitorio");
        when(proxy.ejecutar(CORTE)).thenThrow(fallo).thenReturn(3L);

        var tocadas = servicio.purgarAdjuntos(CORTE);

        verify(proxy, times(2)).ejecutar(CORTE);
        verifyNoInteractions(incidencias);
        assertThat(tocadas).isEqualTo(3L);
    }

    @Test
    void purgarAdjuntos_agotaLosReintentosYAbreIncidenciaConLaCausaDelUltimoFalloYDevuelveCero() {
        var proxy = mock(ServicioPurgarAdjuntos.class);
        servicio.establecerSelf(proxy);
        when(proxy.ejecutar(CORTE)).thenThrow(
                new RuntimeException("boom1"), new RuntimeException("boom2"), new RuntimeException("boom3"),
                new RuntimeException("boom4"), new RuntimeException("boom5"));

        var tocadas = servicio.purgarAdjuntos(CORTE);

        verify(proxy, times(5)).ejecutar(CORTE);
        verify(incidencias).abrir(eq("purga-adjuntos"), eq("RuntimeException: boom5"), eq(5));
        assertThat(tocadas).isZero();
    }

    @Test
    void establecerSelf_sustituyeElProxyUsadoParaLaAutoInvocacionTransaccional() {
        var proxy = mock(ServicioPurgarAdjuntos.class);
        servicio.establecerSelf(proxy);
        when(proxy.ejecutar(CORTE)).thenReturn(0L);

        servicio.purgarAdjuntos(CORTE);

        verify(proxy).ejecutar(CORTE);
    }
}
