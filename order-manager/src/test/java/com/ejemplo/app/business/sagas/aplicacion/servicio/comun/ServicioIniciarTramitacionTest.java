package com.ejemplo.app.business.sagas.aplicacion.servicio.comun;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.ejemplo.app.business.ordermanager.dominio.ExternalId;
import com.ejemplo.app.business.ordermanager.dominio.OrdenId;
import com.ejemplo.app.business.sagas.aplicacion.puerto.entrada.CasoUsoIniciarTramitacion.ComandoIniciarTramitacion;
import com.ejemplo.app.business.sagas.aplicacion.puerto.salida.PuertoDatosNegocio;
import com.ejemplo.app.business.sagas.aplicacion.puerto.salida.PuertoDatosNegocio.RespuestaDatosNegocio;
import com.ejemplo.app.business.sagas.aplicacion.puerto.salida.RepositorioDatosNegocio;
import com.ejemplo.app.business.sagas.dominio.datosnegocio.DatoNegocio1;
import com.ejemplo.app.business.sagas.dominio.datosnegocio.DatoNegocio2;
import com.ejemplo.app.business.sagas.dominio.datosnegocio.DatoNegocio3;
import com.ejemplo.app.business.sagas.dominio.datosnegocio.DocumentoNegocio;
import com.ejemplo.app.business.sagas.dominio.sagaprincipal.EstadoSagaPrincipal;
import com.ejemplo.app.business.sagas.dominio.sagaprincipal.SagaPrincipal;
import com.ejemplo.app.testsoporte.RepositorioOrdenEnMemoria;

/**
 * Iniciar una tramitación: pide los datos de negocio al servicio externo y,
 * con la respuesta, crea el agregado DatosNegocio y la orden con la saga
 * principal, lista para ejecutarse ya.
 */
class ServicioIniciarTramitacionTest {

    private RepositorioOrdenEnMemoria repo;
    private RepositorioDatosNegocio repoDatos;
    private PuertoDatosNegocio puertoDatosNegocio;
    private ServicioIniciarTramitacion servicio;

    @BeforeEach
    void init() {
        repo = new RepositorioOrdenEnMemoria();
        repoDatos = mock(RepositorioDatosNegocio.class);
        puertoDatosNegocio = mock(PuertoDatosNegocio.class);
        servicio = new ServicioIniciarTramitacion(repo, repoDatos, puertoDatosNegocio);
    }

    private static RespuestaDatosNegocio respuesta() {
        return new RespuestaDatosNegocio(new DatoNegocio1(1), new DatoNegocio2(LocalDate.of(2026, 1, 1)),
                new DatoNegocio3("dato"), List.of(new DocumentoNegocio("f.pdf", "application/pdf", new byte[] {1})));
    }

    @Test
    void iniciar_pideLosDatosDeNegocioYCreaElAgregadoDeDatosYLaOrdenConLaSagaPrincipalListaParaEjecutarseYa() {
        var externalId = ExternalId.de(UUID.randomUUID().toString());
        var cmd = new ComandoIniciarTramitacion(externalId);
        var respuesta = respuesta();
        when(puertoDatosNegocio.obtener(externalId)).thenReturn(respuesta);

        var antes = Instant.now();
        var sagaId = servicio.iniciar(cmd);
        var despues = Instant.now();

        verify(repoDatos).crear(any(), eq(respuesta.documentos()));
        var orden = repo.estadoActual(sagaId);
        assertThat(orden).isNotNull();
        assertThat(((SagaPrincipal) orden.proceso()).estado()).isEqualTo(EstadoSagaPrincipal.INICIAL);
        assertThat(orden.proximoReintentoEn()).isBetween(antes, despues);
    }

    @Test
    void establecerSelf_sustituyeElProxyUsadoParaLaAutoInvocacionTransaccional() {
        var proxy = mock(ServicioIniciarTramitacion.class);
        var externalId = ExternalId.de(UUID.randomUUID().toString());
        var respuesta = respuesta();
        when(puertoDatosNegocio.obtener(externalId)).thenReturn(respuesta);
        var idEsperado = OrdenId.nuevo();
        when(proxy.crearAgregados(externalId, respuesta)).thenReturn(idEsperado);

        servicio.establecerSelf(proxy);
        var sagaId = servicio.iniciar(new ComandoIniciarTramitacion(externalId));

        assertThat(sagaId).isEqualTo(idEsperado);
        verify(proxy).crearAgregados(externalId, respuesta);
    }
}
