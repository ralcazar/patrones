package com.ejemplo.app.business.sagas.aplicacion.servicio.comun;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
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

import com.ejemplo.app.business.ordermanager.dominio.ExternalId;
import com.ejemplo.app.business.ordermanager.dominio.OrdenId;
import com.ejemplo.app.business.ordermanager.dominio.Prioridad;
import com.ejemplo.app.business.sagas.aplicacion.puerto.entrada.CasoUsoIniciarTramitacion.ComandoIniciarTramitacion;
import com.ejemplo.app.business.sagas.aplicacion.puerto.salida.PuertoBusquedaTramitacion;
import com.ejemplo.app.business.sagas.aplicacion.puerto.salida.PuertoDatosNegocio;
import com.ejemplo.app.business.sagas.aplicacion.puerto.salida.PuertoDatosNegocio.RespuestaDatosNegocio;
import com.ejemplo.app.business.sagas.aplicacion.puerto.salida.RepositorioDatosNegocio;
import com.ejemplo.app.business.sagas.dominio.datosnegocio.DatoNegocio1;
import com.ejemplo.app.business.sagas.dominio.datosnegocio.DatoNegocio2;
import com.ejemplo.app.business.sagas.dominio.datosnegocio.DatoNegocio3;
import com.ejemplo.app.business.sagas.dominio.datosnegocio.DocumentoNegocio;
import com.ejemplo.app.business.sagas.dominio.datosnegocio.ExternalIdDuplicadoException;
import com.ejemplo.app.business.sagas.dominio.sagaprincipal.EstadoSagaPrincipal;
import com.ejemplo.app.business.sagas.dominio.sagaprincipal.SagaPrincipal;
import com.ejemplo.app.testsoporte.RepositorioOrdenEnMemoria;

/**
 * Iniciar una tramitación: pide los datos de negocio al servicio externo y,
 * con la respuesta, crea el agregado DatosNegocio y la orden con la saga
 * principal, lista para ejecutarse ya. Idempotente: si ya existe la orden
 * principal para el externalId (reintento del cliente o carrera de dos POST
 * simultáneos), devuelve la existente en vez de duplicar la tramitación.
 */
class ServicioIniciarTramitacionTest {

    private RepositorioOrdenEnMemoria repo;
    private RepositorioDatosNegocio repoDatos;
    private PuertoDatosNegocio puertoDatosNegocio;
    private PuertoBusquedaTramitacion busqueda;
    private ServicioIniciarTramitacion servicio;

    @BeforeEach
    void init() {
        repo = new RepositorioOrdenEnMemoria();
        repoDatos = mock(RepositorioDatosNegocio.class);
        puertoDatosNegocio = mock(PuertoDatosNegocio.class);
        busqueda = mock(PuertoBusquedaTramitacion.class);
        when(busqueda.ordenPrincipalDe(any())).thenReturn(Optional.empty());
        servicio = new ServicioIniciarTramitacion(repo, repoDatos, puertoDatosNegocio, busqueda);
    }

    private static RespuestaDatosNegocio respuesta() {
        return respuesta(new DatoNegocio3("dato"));
    }

    private static RespuestaDatosNegocio respuesta(DatoNegocio3 datoNegocio3) {
        return new RespuestaDatosNegocio(new DatoNegocio1(1), new DatoNegocio2(LocalDate.of(2026, 1, 1)),
                datoNegocio3, List.of(new DocumentoNegocio("f.pdf", "application/pdf", new byte[] {1})));
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
    void iniciar_conDatoNegocio3Origen2_laOrdenPrincipalCreadaLlevaLaPrioridadDerivada() {
        var externalId = ExternalId.de(UUID.randomUUID().toString());
        var cmd = new ComandoIniciarTramitacion(externalId);
        var respuesta = respuesta(new DatoNegocio3("ORIGEN2"));
        when(puertoDatosNegocio.obtener(externalId)).thenReturn(respuesta);

        var sagaId = servicio.iniciar(cmd);

        var orden = repo.estadoActual(sagaId);
        assertThat(orden.prioridad()).isEqualTo(new Prioridad(30));
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

    @Test
    void iniciar_conTramitacionYaExistenteDevuelveLaExistenteSinLlamarAlServicioExternoNiCrearNada() {
        var externalId = ExternalId.de(UUID.randomUUID().toString());
        var ordenExistente = OrdenId.nuevo();
        when(busqueda.ordenPrincipalDe(externalId)).thenReturn(Optional.of(ordenExistente));

        var sagaId = servicio.iniciar(new ComandoIniciarTramitacion(externalId));

        assertThat(sagaId).isEqualTo(ordenExistente);
        verifyNoInteractions(puertoDatosNegocio);
        verify(repoDatos, never()).crear(any(), any());
    }

    @Test
    void iniciar_conCarreraDeDosPostSimultaneosDevuelveLaOrdenDelGanador() {
        var proxy = mock(ServicioIniciarTramitacion.class);
        var externalId = ExternalId.de(UUID.randomUUID().toString());
        var respuesta = respuesta();
        when(puertoDatosNegocio.obtener(externalId)).thenReturn(respuesta);
        var conflicto = new ExternalIdDuplicadoException(externalId, null);
        when(proxy.crearAgregados(externalId, respuesta)).thenThrow(conflicto);
        var ordenGanadora = OrdenId.nuevo();
        when(busqueda.ordenPrincipalDe(externalId))
                .thenReturn(Optional.empty()) // primera consulta: aún no hay nada
                .thenReturn(Optional.of(ordenGanadora)); // segunda (en el catch): ya comiteó la otra petición
        servicio.establecerSelf(proxy);

        var sagaId = servicio.iniciar(new ComandoIniciarTramitacion(externalId));

        assertThat(sagaId).isEqualTo(ordenGanadora);
    }

    @Test
    void iniciar_conConflictoYSegundaConsultaTambienVaciaPropagaLaExcepcion() {
        var proxy = mock(ServicioIniciarTramitacion.class);
        var externalId = ExternalId.de(UUID.randomUUID().toString());
        var respuesta = respuesta();
        when(puertoDatosNegocio.obtener(externalId)).thenReturn(respuesta);
        var conflicto = new ExternalIdDuplicadoException(externalId, null);
        when(proxy.crearAgregados(externalId, respuesta)).thenThrow(conflicto);
        when(busqueda.ordenPrincipalDe(externalId)).thenReturn(Optional.empty());
        servicio.establecerSelf(proxy);

        assertThatThrownBy(() -> servicio.iniciar(new ComandoIniciarTramitacion(externalId)))
                .isSameAs(conflicto);
    }
}
