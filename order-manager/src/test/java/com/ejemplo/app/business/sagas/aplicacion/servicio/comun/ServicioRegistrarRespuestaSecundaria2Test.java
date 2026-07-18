package com.ejemplo.app.business.sagas.aplicacion.servicio.comun;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.ejemplo.app.business.ordermanager.aplicacion.puerto.salida.PuertoMensajesProcesados;
import com.ejemplo.app.testsoporte.RepositorioOrdenEnMemoria;
import com.ejemplo.app.business.sagas.dominio.comun.ContextoArranque;
import com.ejemplo.app.business.ordermanager.dominio.ExternalId;
import com.ejemplo.app.business.ordermanager.dominio.OrdenRoot;
import com.ejemplo.app.business.ordermanager.dominio.PoliticaReintentos;
import com.ejemplo.app.business.sagas.dominio.comun.RefPaso5;
import com.ejemplo.app.business.ordermanager.dominio.OrdenId;
import com.ejemplo.app.business.sagas.dominio.sagasecundaria2.EstadoSagaSecundaria2;
import com.ejemplo.app.business.sagas.dominio.sagasecundaria2.RefRespuesta;
import com.ejemplo.app.business.sagas.dominio.sagasecundaria2.SagaSecundaria2;

/**
 * Consumer de Kafka -&gt; caso de uso que aplica directamente la respuesta
 * diferida de la secundaria 2: dedup por mensajeId y despertar/reintento
 * (Fase 4 del refactor).
 */
class ServicioRegistrarRespuestaSecundaria2Test {

    private RepositorioOrdenEnMemoria repo;
    private PuertoMensajesProcesados dedup;
    private ServicioRegistrarRespuestaSecundaria2 servicio;

    @BeforeEach
    void init() {
        repo = new RepositorioOrdenEnMemoria();
        dedup = mock(PuertoMensajesProcesados.class);
        servicio = new ServicioRegistrarRespuestaSecundaria2(repo, dedup, new PoliticaReintentos());
    }

    private OrdenId crearOrdenEsperandoRespuesta() {
        var id = OrdenId.nuevo();
        var ctx = new ContextoArranque.ArranqueSecundaria2(
                ExternalId.de(UUID.randomUUID().toString()), new RefPaso5("ref5"));
        var saga = SagaSecundaria2.crear(id, ctx);
        saga.solicitudEnviada();
        var orden = OrdenRoot.nueva(saga, Instant.now());
        orden.aparcar(Duration.ofHours(3), Instant.now());
        repo.crear(orden);
        return id;
    }

    @Test
    void respuestaOk_aplicaLaRespuestaYDespiertaLaOrden() {
        var id = crearOrdenEsperandoRespuesta();
        when(dedup.yaProcesado(any())).thenReturn(false);

        servicio.respuestaOk(id, new RefRespuesta("resp-1"), "msg-1");

        var orden = repo.estadoActual(id);
        assertThat(((SagaSecundaria2) orden.proceso()).estado()).isEqualTo(EstadoSagaSecundaria2.TERMINADA);
        assertThat(((SagaSecundaria2) orden.proceso()).refRespuesta().valor()).isEqualTo("resp-1");
        assertThat(orden.tokenTrabajador()).isNull();
        assertThat(orden.proximoReintentoEn()).isBeforeOrEqualTo(Instant.now());
        verify(dedup).registrar(any());
    }

    @Test
    void respuestaOk_siYaSeProceso_noHaceNadaMas() {
        var id = crearOrdenEsperandoRespuesta();
        when(dedup.yaProcesado(any())).thenReturn(true);

        servicio.respuestaOk(id, new RefRespuesta("resp-1"), "msg-1");

        assertThat(((SagaSecundaria2) repo.estadoActual(id).proceso()).estado())
                .isEqualTo(EstadoSagaSecundaria2.ESPERANDO_RESPUESTA);
        verify(dedup, never()).registrar(any());
    }

    @Test
    void respuestaError_vuelveASolicitarYProgramaElPrimerReintento() {
        var id = crearOrdenEsperandoRespuesta();
        when(dedup.yaProcesado(any())).thenReturn(false);

        servicio.respuestaError(id, "ERR", "detalle", true, "msg-1");

        var orden = repo.estadoActual(id);
        assertThat(((SagaSecundaria2) orden.proceso()).estado()).isEqualTo(EstadoSagaSecundaria2.INICIAL);
        assertThat(orden.intentos()).isEqualTo(1);
        assertThat(orden.proximoReintentoEn())
                .isBetween(Instant.now().plus(Duration.ofSeconds(55)), Instant.now().plus(Duration.ofMinutes(1).plusSeconds(5)));
    }

    @Test
    void respuestaError_siYaSeProceso_noHaceNadaMas() {
        var id = crearOrdenEsperandoRespuesta();
        when(dedup.yaProcesado(any())).thenReturn(true);

        servicio.respuestaError(id, "ERR", "detalle", true, "msg-1");

        assertThat(((SagaSecundaria2) repo.estadoActual(id).proceso()).estado())
                .isEqualTo(EstadoSagaSecundaria2.ESPERANDO_RESPUESTA);
        verify(dedup, never()).registrar(any());
    }
}
