package com.ejemplo.app.business.ordermanager.aplicacion.servicio;

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

import com.ejemplo.app.business.ordermanager.aplicacion.puerto.salida.PuertoConciliacionSecundaria2;
import com.ejemplo.app.business.ordermanager.aplicacion.puerto.salida.PuertoSagaSecundaria2;
import com.ejemplo.app.business.ordermanager.aplicacion.servicio.soporte.RepositorioOrdenEnMemoria;
import com.ejemplo.app.business.ordermanager.aplicacion.servicio.soporte.UnidadDeTrabajoInmediata;
import com.ejemplo.app.business.ordermanager.dominio.comun.ContextoArranque;
import com.ejemplo.app.business.ordermanager.dominio.comun.ExcepcionServicioExterno;
import com.ejemplo.app.business.ordermanager.dominio.comun.ExternalId;
import com.ejemplo.app.business.ordermanager.dominio.comun.MotivoFallo;
import com.ejemplo.app.business.ordermanager.dominio.comun.OrdenRoot;
import com.ejemplo.app.business.ordermanager.dominio.comun.RefPaso5;
import com.ejemplo.app.business.ordermanager.dominio.comun.ResultadoOrden;
import com.ejemplo.app.business.ordermanager.dominio.comun.SagaId;
import com.ejemplo.app.business.ordermanager.dominio.sagasecundaria2.EstadoSagaSecundaria2;
import com.ejemplo.app.business.ordermanager.dominio.sagasecundaria2.RefRespuesta;
import com.ejemplo.app.business.ordermanager.dominio.sagasecundaria2.SagaSecundaria2Root;

/**
 * Orquestador de la saga secundaria 2: solicitud REST + ventana de espera de
 * 3h vigilada por conciliación (Fase 4 del refactor: escenario "secundaria2
 * 3h + despertar").
 */
class ServicioSagaSecundaria2Test {

    private RepositorioOrdenEnMemoria repo;
    private UnidadDeTrabajoInmediata tx;
    private PuertoSagaSecundaria2 puerto;
    private PuertoConciliacionSecundaria2 conciliacion;
    private ServicioSagaSecundaria2 orquestador;

    @BeforeEach
    void init() {
        repo = new RepositorioOrdenEnMemoria();
        tx = new UnidadDeTrabajoInmediata();
        puerto = mock(PuertoSagaSecundaria2.class);
        conciliacion = mock(PuertoConciliacionSecundaria2.class);
        orquestador = new ServicioSagaSecundaria2(repo, tx, puerto, conciliacion);
    }

    private SagaId crearOrdenSecundaria2() {
        var id = SagaId.nuevo();
        var ctx = new ContextoArranque.ArranqueSecundaria2(
                ExternalId.de(UUID.randomUUID().toString()), new RefPaso5("ref5"));
        repo.crear(OrdenRoot.nueva(SagaSecundaria2Root.crear(id, ctx), Instant.now()));
        return id;
    }

    @Test
    void inicial_solicitaYAparcaLaOrdenDurantesTresHoras() {
        var id = crearOrdenSecundaria2();

        var antes = Instant.now();
        var senal = orquestador.ejecutarPaso(id);
        var despues = Instant.now();

        assertThat(senal).isInstanceOf(SenalPaso.Aparcar.class);
        assertThat(((SenalPaso.Aparcar) senal).ventana()).isEqualTo(Duration.ofHours(3));
        verify(puerto).solicitar(org.mockito.ArgumentMatchers.eq(id), any());
        var orden = repo.estadoActual(id);
        assertThat(((SagaSecundaria2Root) orden.saga()).estado()).isEqualTo(EstadoSagaSecundaria2.ESPERANDO_RESPUESTA);
        assertThat(orden.tokenTrabajador()).isNull();
        assertThat(orden.proximoReintentoEn())
                .isBetween(antes.plus(Duration.ofHours(3)), despues.plus(Duration.ofHours(3)));
    }

    @Test
    void esperandoRespuesta_siLaConciliacionEncuentraElResultado_finalizaLaOrden() {
        var id = crearOrdenSecundaria2();
        orquestador.ejecutarPaso(id); // INICIAL -> ESPERANDO_RESPUESTA
        when(conciliacion.consultar(any(), any()))
                .thenReturn(new PuertoConciliacionSecundaria2.Resultado.Disponible(new RefRespuesta("resp-1")));

        var senal = orquestador.ejecutarPaso(id);

        assertThat(senal).isInstanceOf(SenalPaso.Finalizada.class);
        assertThat(((SenalPaso.Finalizada) senal).resultado()).isEqualTo(ResultadoOrden.FINALIZADA_OK);
        var orden = repo.estadoActual(id);
        assertThat(orden.estaViva()).isFalse();
        assertThat(((SagaSecundaria2Root) orden.saga()).estado()).isEqualTo(EstadoSagaSecundaria2.TERMINADA);
        assertThat(((SagaSecundaria2Root) orden.saga()).refRespuesta().valor()).isEqualTo("resp-1");
    }

    @Test
    void esperandoRespuesta_siLaConciliacionNoTieneResultado_vuelveAAparcarOtrasTresHoras() {
        var id = crearOrdenSecundaria2();
        orquestador.ejecutarPaso(id);
        when(conciliacion.consultar(any(), any())).thenReturn(new PuertoConciliacionSecundaria2.Resultado.SinResultado());

        var senal = orquestador.ejecutarPaso(id);

        assertThat(senal).isInstanceOf(SenalPaso.Aparcar.class);
        assertThat(((SenalPaso.Aparcar) senal).ventana()).isEqualTo(Duration.ofHours(3));
        var orden = repo.estadoActual(id);
        assertThat(((SagaSecundaria2Root) orden.saga()).estado()).isEqualTo(EstadoSagaSecundaria2.ESPERANDO_RESPUESTA);
    }

    @Test
    void esperandoRespuesta_siLaConciliacionRegistraUnFallo_lanzaParaQueSeReintente() {
        var id = crearOrdenSecundaria2();
        orquestador.ejecutarPaso(id);
        when(conciliacion.consultar(any(), any()))
                .thenReturn(new PuertoConciliacionSecundaria2.Resultado.FalloRegistrado(MotivoFallo.errorTecnico("boom")));

        assertThatThrownBy(() -> orquestador.ejecutarPaso(id)).isInstanceOf(ExcepcionServicioExterno.class);
        // No se persiste nada al lanzar: ServicioContinuarSaga es quien decide el reintento.
        var orden = repo.estadoActual(id);
        assertThat(((SagaSecundaria2Root) orden.saga()).estado()).isEqualTo(EstadoSagaSecundaria2.ESPERANDO_RESPUESTA);
    }

    @Test
    void kafkaDespiertaLaOrden_yLaSiguientePasadaDelOrquestadorCierraElOperativo() {
        var id = crearOrdenSecundaria2();
        orquestador.ejecutarPaso(id); // INICIAL -> ESPERANDO_RESPUESTA (aparcada 3h)

        // El consumer de Kafka resuelve la saga directamente y despierta la orden (ServicioRegistrarRespuestaSecundaria2).
        var orden = repo.cargar(id);
        var saga = (SagaSecundaria2Root) orden.saga();
        saga.respuestaRecibida(new RefRespuesta("resp-kafka"));
        orden.despertar(Instant.now());
        repo.guardar(orden);

        // El orquestador, en su siguiente pasada, deja el cierre operativo (finalizar) de la orden.
        var senal = orquestador.ejecutarPaso(id);

        assertThat(senal).isInstanceOf(SenalPaso.Finalizada.class);
        assertThat(repo.estadoActual(id).estaViva()).isFalse();
    }
}
