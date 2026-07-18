package com.ejemplo.app.business.sagas.aplicacion.servicio.sagasecundaria3;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.ejemplo.app.business.ordermanager.aplicacion.servicio.SenalPaso;
import com.ejemplo.app.business.ordermanager.dominio.ExternalId;
import com.ejemplo.app.business.ordermanager.dominio.OrdenId;
import com.ejemplo.app.business.ordermanager.dominio.OrdenRoot;
import com.ejemplo.app.business.sagas.aplicacion.puerto.salida.PuertoSagaSecundaria3;
import com.ejemplo.app.business.sagas.dominio.comun.ContextoArranque;
import com.ejemplo.app.business.sagas.dominio.comun.RefPaso7;
import com.ejemplo.app.business.sagas.dominio.sagasecundaria3.ComandoPasoSecundaria3;
import com.ejemplo.app.business.sagas.dominio.sagasecundaria3.EstadoSagaSecundaria3;
import com.ejemplo.app.business.sagas.dominio.sagasecundaria3.RefEjecucion;
import com.ejemplo.app.business.sagas.dominio.sagasecundaria3.ResultadoPasoSecundaria3;
import com.ejemplo.app.business.sagas.dominio.sagasecundaria3.SagaSecundaria3;
import com.ejemplo.app.testsoporte.RepositorioOrdenEnMemoria;

/** Servicio de la saga secundaria 3: una única llamada REST síncrona, nunca se cancela ni compensa. */
class ServicioSagaSecundaria3Test {

    private RepositorioOrdenEnMemoria repo;
    private PuertoSagaSecundaria3 puerto;
    private ServicioSagaSecundaria3 servicioSaga;

    @BeforeEach
    void init() {
        repo = new RepositorioOrdenEnMemoria();
        puerto = mock(PuertoSagaSecundaria3.class);
        servicioSaga = new ServicioSagaSecundaria3(repo, puerto);
    }

    private OrdenId crearOrdenSecundaria3() {
        var id = OrdenId.nuevo();
        var ctx = new ContextoArranque.ArranqueSecundaria3(
                ExternalId.de(UUID.randomUUID().toString()), new RefPaso7("ref7"));
        repo.crear(OrdenRoot.nueva(SagaSecundaria3.crear(id, ctx), Instant.now()));
        return id;
    }

    @Test
    void ejecutarPaso_ejecutaYFinalizaLaOrdenConLaRef() {
        var id = crearOrdenSecundaria3();
        when(puerto.ejecutar(any())).thenReturn(new ResultadoPasoSecundaria3.Ejecutada(new RefEjecucion("ejec1")));

        var senal = servicioSaga.ejecutarPaso(repo.cargar(id));

        assertThat(senal).isInstanceOf(SenalPaso.Finalizada.class);
        verify(puerto).ejecutar(any(ComandoPasoSecundaria3.Ejecutar.class));
        var orden = repo.estadoActual(id);
        var saga = (SagaSecundaria3) orden.proceso();
        assertThat(saga.estado()).isEqualTo(EstadoSagaSecundaria3.TERMINADA);
        assertThat(saga.refEjecucion().valor()).isEqualTo("ejec1");
        assertThat(orden.estaViva()).isFalse();
    }

    @Test
    void tipo_devuelveElTipoDeLaSagaSecundaria3() {
        assertThat(servicioSaga.tipo()).isEqualTo(SagaSecundaria3.TIPO);
    }

    @Test
    void establecerSelf_sustituyeElProxyUsadoParaLaAutoInvocacionTransaccional() {
        var proxy = mock(ServicioSagaSecundaria3.class);
        var id = crearOrdenSecundaria3();
        when(puerto.ejecutar(any())).thenReturn(new ResultadoPasoSecundaria3.Ejecutada(new RefEjecucion("ejec1")));
        when(proxy.aplicar(any(), any(), any())).thenReturn(new SenalPaso.Finalizada(
                com.ejemplo.app.business.ordermanager.dominio.ResultadoOrden.FINALIZADA_OK));

        servicioSaga.establecerSelf(proxy);
        var senal = servicioSaga.ejecutarPaso(repo.cargar(id));

        assertThat(senal).isInstanceOf(SenalPaso.Finalizada.class);
        verify(proxy).aplicar(any(), any(), any());
    }
}
