package com.ejemplo.app.business.sagas.aplicacion.servicio.sagasecundaria1;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.ejemplo.app.business.ordermanager.aplicacion.servicio.SenalPaso;
import com.ejemplo.app.business.ordermanager.dominio.ExternalId;
import com.ejemplo.app.business.ordermanager.dominio.OrdenId;
import com.ejemplo.app.business.ordermanager.dominio.OrdenRoot;
import com.ejemplo.app.business.sagas.aplicacion.puerto.salida.PuertoSagaSecundaria1;
import com.ejemplo.app.business.sagas.dominio.comun.ContextoArranque;
import com.ejemplo.app.business.sagas.dominio.comun.RefPaso1;
import com.ejemplo.app.business.sagas.dominio.sagasecundaria1.ComandoPasoSecundaria1;
import com.ejemplo.app.business.sagas.dominio.sagasecundaria1.EstadoSagaSecundaria1;
import com.ejemplo.app.business.sagas.dominio.sagasecundaria1.RefConfirmacion;
import com.ejemplo.app.business.sagas.dominio.sagasecundaria1.RefInicio;
import com.ejemplo.app.business.sagas.dominio.sagasecundaria1.ResultadoPasoSecundaria1;
import com.ejemplo.app.business.sagas.dominio.sagasecundaria1.SagaSecundaria1;
import com.ejemplo.app.testsoporte.RepositorioOrdenEnMemoria;

/**
 * Servicio de la saga secundaria 1: dos llamadas REST síncronas encadenadas
 * (INICIO -&gt; CONFIRMACION), nunca se cancela ni compensa.
 */
class ServicioSagaSecundaria1Test {

    private static final Duration LEASE = Duration.ofMinutes(5);

    private RepositorioOrdenEnMemoria repo;
    private PuertoSagaSecundaria1 puerto;
    private ServicioSagaSecundaria1 servicioSaga;

    @BeforeEach
    void init() {
        repo = new RepositorioOrdenEnMemoria();
        puerto = mock(PuertoSagaSecundaria1.class);
        servicioSaga = new ServicioSagaSecundaria1(repo, LEASE, puerto);
    }

    private OrdenId crearOrdenSecundaria1() {
        var id = OrdenId.nuevo();
        var ctx = new ContextoArranque.ArranqueSecundaria1(
                ExternalId.de(UUID.randomUUID().toString()), new RefPaso1("ref1"));
        repo.crear(OrdenRoot.nueva(SagaSecundaria1.crear(id, ctx), Instant.now()));
        return id;
    }

    @Test
    void inicial_ejecutaIniciarYDejaHayMasTrabajoConLeaseRenovado() {
        var id = crearOrdenSecundaria1();
        when(puerto.iniciar(any())).thenReturn(new ResultadoPasoSecundaria1.Iniciada(new RefInicio("ini1")));

        var senal = servicioSaga.ejecutarPaso(repo.cargar(id));

        assertThat(senal).isInstanceOf(SenalPaso.HayMasTrabajo.class);
        verify(puerto).iniciar(any(ComandoPasoSecundaria1.Iniciar.class));
        var orden = repo.estadoActual(id);
        var saga = (SagaSecundaria1) orden.proceso();
        assertThat(saga.estado()).isEqualTo(EstadoSagaSecundaria1.INICIO_HECHO);
        assertThat(saga.refInicio().valor()).isEqualTo("ini1");
        assertThat(orden.estaViva()).isTrue();
    }

    @Test
    void inicioHecho_ejecutaConfirmarYFinalizaLaOrden() {
        var id = crearOrdenSecundaria1();
        when(puerto.iniciar(any())).thenReturn(new ResultadoPasoSecundaria1.Iniciada(new RefInicio("ini1")));
        servicioSaga.ejecutarPaso(repo.cargar(id)); // INICIAL -> INICIO_HECHO
        when(puerto.confirmar(any())).thenReturn(new ResultadoPasoSecundaria1.Confirmada(new RefConfirmacion("conf1")));

        var senal = servicioSaga.ejecutarPaso(repo.cargar(id));

        assertThat(senal).isInstanceOf(SenalPaso.Finalizada.class);
        verify(puerto).confirmar(any(ComandoPasoSecundaria1.Confirmar.class));
        var orden = repo.estadoActual(id);
        var saga = (SagaSecundaria1) orden.proceso();
        assertThat(saga.estado()).isEqualTo(EstadoSagaSecundaria1.TERMINADA);
        assertThat(saga.refConfirmacion().valor()).isEqualTo("conf1");
        assertThat(orden.estaViva()).isFalse();
    }

    @Test
    void tipo_devuelveElTipoDeLaSagaSecundaria1() {
        assertThat(servicioSaga.tipo()).isEqualTo(SagaSecundaria1.TIPO);
    }

    @Test
    void establecerSelf_sustituyeElProxyUsadoParaLaAutoInvocacionTransaccional() {
        var proxy = mock(ServicioSagaSecundaria1.class);
        var id = crearOrdenSecundaria1();
        when(puerto.iniciar(any())).thenReturn(new ResultadoPasoSecundaria1.Iniciada(new RefInicio("ini1")));
        when(proxy.aplicar(any(), any(), any())).thenReturn(new SenalPaso.HayMasTrabajo(repo.cargar(id)));

        servicioSaga.establecerSelf(proxy);
        var senal = servicioSaga.ejecutarPaso(repo.cargar(id));

        assertThat(senal).isInstanceOf(SenalPaso.HayMasTrabajo.class);
        verify(proxy).aplicar(any(), any(), any());
    }
}
