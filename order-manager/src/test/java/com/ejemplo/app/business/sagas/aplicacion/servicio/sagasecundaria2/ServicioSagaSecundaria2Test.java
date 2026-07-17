package com.ejemplo.app.business.sagas.aplicacion.servicio.sagasecundaria2;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.ejemplo.app.business.sagas.aplicacion.puerto.salida.PuertoConciliacionSecundaria2;
import com.ejemplo.app.business.sagas.aplicacion.puerto.salida.PuertoSagaSecundaria2;
import com.ejemplo.app.business.ordermanager.aplicacion.servicio.SenalPaso;
import com.ejemplo.app.testsoporte.RepositorioOrdenEnMemoria;
import com.ejemplo.app.business.sagas.dominio.comun.ContextoArranque;
import com.ejemplo.app.business.ordermanager.dominio.ExcepcionServicioExterno;
import com.ejemplo.app.business.ordermanager.dominio.ExternalId;
import com.ejemplo.app.business.ordermanager.dominio.MotivoFallo;
import com.ejemplo.app.business.ordermanager.dominio.OrdenRoot;
import com.ejemplo.app.business.sagas.dominio.comun.RefPaso5;
import com.ejemplo.app.business.ordermanager.dominio.ResultadoOrden;
import com.ejemplo.app.business.ordermanager.dominio.OrdenId;
import com.ejemplo.app.business.sagas.dominio.sagasecundaria2.EstadoSagaSecundaria2;
import com.ejemplo.app.business.sagas.dominio.sagasecundaria2.RefRespuesta;
import com.ejemplo.app.business.sagas.dominio.sagasecundaria2.SagaSecundaria2;

/**
 * Servicio de la saga secundaria 2: solicitud REST + ventana de espera de
 * 3h vigilada por conciliación (Fase 4 del refactor: escenario "secundaria2
 * 3h + despertar").
 */
class ServicioSagaSecundaria2Test {

    private RepositorioOrdenEnMemoria repo;
    private PuertoSagaSecundaria2 puerto;
    private PuertoConciliacionSecundaria2 conciliacion;
    private ServicioSagaSecundaria2 servicioSaga;

    @BeforeEach
    void init() {
        repo = new RepositorioOrdenEnMemoria();
        puerto = mock(PuertoSagaSecundaria2.class);
        conciliacion = mock(PuertoConciliacionSecundaria2.class);
        servicioSaga = new ServicioSagaSecundaria2(repo, puerto, conciliacion);
    }

    private OrdenId crearOrdenSecundaria2() {
        var id = OrdenId.nuevo();
        var ctx = new ContextoArranque.ArranqueSecundaria2(
                ExternalId.de(UUID.randomUUID().toString()), new RefPaso5("ref5"));
        repo.crear(OrdenRoot.nueva(SagaSecundaria2.crear(id, ctx), Instant.now()));
        return id;
    }

    @Test
    void inicial_solicitaYAparcaLaOrdenDurantesTresHoras() {
        var id = crearOrdenSecundaria2();

        var antes = Instant.now();
        var senal = servicioSaga.ejecutarPaso(repo.cargar(id));
        var despues = Instant.now();

        assertThat(senal).isInstanceOf(SenalPaso.Aparcar.class);
        assertThat(((SenalPaso.Aparcar) senal).ventana()).isEqualTo(Duration.ofHours(3));
        verify(puerto).solicitar(org.mockito.ArgumentMatchers.eq(id), any());
        var orden = repo.estadoActual(id);
        assertThat(((SagaSecundaria2) orden.proceso()).estado()).isEqualTo(EstadoSagaSecundaria2.ESPERANDO_RESPUESTA);
        assertThat(orden.tokenTrabajador()).isNull();
        assertThat(orden.proximoReintentoEn())
                .isBetween(antes.plus(Duration.ofHours(3)), despues.plus(Duration.ofHours(3)));
    }

    @Test
    void esperandoRespuesta_siLaConciliacionEncuentraElResultado_finalizaLaOrden() {
        var id = crearOrdenSecundaria2();
        servicioSaga.ejecutarPaso(repo.cargar(id)); // INICIAL -> ESPERANDO_RESPUESTA
        when(conciliacion.consultar(any(), any()))
                .thenReturn(new PuertoConciliacionSecundaria2.Resultado.Disponible(new RefRespuesta("resp-1")));

        var senal = servicioSaga.ejecutarPaso(repo.cargar(id));

        assertThat(senal).isInstanceOf(SenalPaso.Finalizada.class);
        assertThat(((SenalPaso.Finalizada) senal).resultado()).isEqualTo(ResultadoOrden.FINALIZADA_OK);
        var orden = repo.estadoActual(id);
        assertThat(orden.estaViva()).isFalse();
        assertThat(((SagaSecundaria2) orden.proceso()).estado()).isEqualTo(EstadoSagaSecundaria2.TERMINADA);
        assertThat(((SagaSecundaria2) orden.proceso()).refRespuesta().valor()).isEqualTo("resp-1");
    }

    @Test
    void esperandoRespuesta_siLaConciliacionNoTieneResultado_vuelveAAparcarOtrasTresHoras() {
        var id = crearOrdenSecundaria2();
        servicioSaga.ejecutarPaso(repo.cargar(id));
        when(conciliacion.consultar(any(), any())).thenReturn(new PuertoConciliacionSecundaria2.Resultado.SinResultado());

        var senal = servicioSaga.ejecutarPaso(repo.cargar(id));

        assertThat(senal).isInstanceOf(SenalPaso.Aparcar.class);
        assertThat(((SenalPaso.Aparcar) senal).ventana()).isEqualTo(Duration.ofHours(3));
        var orden = repo.estadoActual(id);
        assertThat(((SagaSecundaria2) orden.proceso()).estado()).isEqualTo(EstadoSagaSecundaria2.ESPERANDO_RESPUESTA);
    }

    @Test
    void esperandoRespuesta_siLaConciliacionRegistraUnFallo_lanzaParaQueSeReintente() {
        var id = crearOrdenSecundaria2();
        servicioSaga.ejecutarPaso(repo.cargar(id));
        when(conciliacion.consultar(any(), any()))
                .thenReturn(new PuertoConciliacionSecundaria2.Resultado.FalloRegistrado(MotivoFallo.errorTecnico("boom")));

        assertThatThrownBy(() -> servicioSaga.ejecutarPaso(repo.cargar(id))).isInstanceOf(ExcepcionServicioExterno.class);
        // No se persiste nada al lanzar: ServicioContinuarOrden es quien decide el reintento.
        var orden = repo.estadoActual(id);
        assertThat(((SagaSecundaria2) orden.proceso()).estado()).isEqualTo(EstadoSagaSecundaria2.ESPERANDO_RESPUESTA);
    }

    @Test
    void kafkaDespiertaLaOrden_yLaSiguientePasadaDelServicioSagaCierraElOperativo() {
        var id = crearOrdenSecundaria2();
        servicioSaga.ejecutarPaso(repo.cargar(id)); // INICIAL -> ESPERANDO_RESPUESTA (aparcada 3h)

        // El consumer de Kafka resuelve la saga directamente y despierta la orden (ServicioRegistrarRespuestaSecundaria2).
        var orden = repo.cargar(id);
        var saga = (SagaSecundaria2) orden.proceso();
        saga.respuestaRecibida(new RefRespuesta("resp-kafka"));
        orden.despertar(Instant.now());
        repo.guardar(orden);

        // El servicioSaga, en su siguiente pasada, deja el cierre operativo (finalizar) de la orden.
        var senal = servicioSaga.ejecutarPaso(repo.cargar(id));

        assertThat(senal).isInstanceOf(SenalPaso.Finalizada.class);
        assertThat(repo.estadoActual(id).estaViva()).isFalse();
    }
}
