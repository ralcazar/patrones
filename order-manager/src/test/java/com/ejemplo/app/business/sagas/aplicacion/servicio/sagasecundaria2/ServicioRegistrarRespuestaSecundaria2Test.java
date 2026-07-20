package com.ejemplo.app.business.sagas.aplicacion.servicio.sagasecundaria2;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.ejemplo.app.testsoporte.RepositorioOrdenEnMemoria;
import com.ejemplo.app.business.sagas.dominio.comun.ContextoArranque;
import com.ejemplo.app.business.ordermanager.dominio.ExternalId;
import com.ejemplo.app.business.ordermanager.dominio.OrdenRoot;
import com.ejemplo.app.business.sagas.dominio.comun.RefPaso5;
import com.ejemplo.app.business.ordermanager.dominio.OrdenId;
import com.ejemplo.app.business.sagas.dominio.sagasecundaria2.EstadoSagaSecundaria2;
import com.ejemplo.app.business.sagas.dominio.sagasecundaria2.RefRespuesta;
import com.ejemplo.app.business.sagas.dominio.sagasecundaria2.SagaSecundaria2;

/**
 * Consumer de Kafka -&gt; caso de uso que aplica directamente la respuesta
 * diferida de la secundaria 2 (Fase 4 del refactor). El evento real solo
 * trae éxito: la idempotencia ante duplicados descansa en el guard de
 * abajo, no en deduplicación por mensajeId (ver javadoc del servicio).
 */
class ServicioRegistrarRespuestaSecundaria2Test {

    private RepositorioOrdenEnMemoria repo;
    private ServicioRegistrarRespuestaSecundaria2 servicio;

    @BeforeEach
    void init() {
        repo = new RepositorioOrdenEnMemoria();
        servicio = new ServicioRegistrarRespuestaSecundaria2(repo);
    }

    private OrdenId crearOrdenEsperandoRespuesta() {
        var id = OrdenId.nuevo();
        var ctx = new ContextoArranque.ArranqueSecundaria2(
                ExternalId.de(UUID.randomUUID().toString()), new RefPaso5("ref5"));
        var saga = SagaSecundaria2.crear(id, ctx);
        saga = saga.solicitudEnviada();
        var orden = OrdenRoot.nueva(saga, Instant.now());
        orden.aparcar(Duration.ofHours(3), Instant.now());
        repo.crear(orden);
        return id;
    }

    @Test
    void respuestaOk_aplicaLaRespuestaYDespiertaLaOrden() {
        var id = crearOrdenEsperandoRespuesta();

        servicio.respuestaOk(id, new RefRespuesta("resp-1"));

        var orden = repo.estadoActual(id);
        assertThat(((SagaSecundaria2) orden.proceso()).estado()).isEqualTo(EstadoSagaSecundaria2.TERMINADA);
        assertThat(((SagaSecundaria2) orden.proceso()).refRespuesta().valor()).isEqualTo("resp-1");
        assertThat(orden.tokenTrabajador()).isNull();
        assertThat(orden.proximoReintentoEn()).isBeforeOrEqualTo(Instant.now());
    }

    @Test
    void respuestaOk_procesoYaTerminado_noEscribeDeNuevo() {
        var id = crearOrdenEsperandoRespuesta();
        servicio.respuestaOk(id, new RefRespuesta("resp-1"));

        servicio.respuestaOk(id, new RefRespuesta("resp-2"));

        var orden = repo.estadoActual(id);
        assertThat(((SagaSecundaria2) orden.proceso()).refRespuesta().valor()).isEqualTo("resp-1");
    }

    @Test
    void respuestaOk_ordenYaNoViva_noEscribeDeNuevo() {
        var id = OrdenId.nuevo();
        var ctx = new ContextoArranque.ArranqueSecundaria2(
                ExternalId.de(UUID.randomUUID().toString()), new RefPaso5("ref5"));
        var saga = SagaSecundaria2.crear(id, ctx).solicitudEnviada();
        var orden = OrdenRoot.nueva(saga, Instant.now());
        orden.aparcar(Duration.ofHours(3), Instant.now());
        orden.finalizar(Instant.now());
        repo.crear(orden);

        servicio.respuestaOk(id, new RefRespuesta("resp-1"));

        var actual = repo.estadoActual(id);
        assertThat(((SagaSecundaria2) actual.proceso()).estado()).isEqualTo(EstadoSagaSecundaria2.ESPERANDO_RESPUESTA);
    }

    @Test
    void respuestaOk_ordenYaPurgada_noLanza() {
        var id = OrdenId.nuevo();

        assertThatCode(() -> servicio.respuestaOk(id, new RefRespuesta("resp-1"))).doesNotThrowAnyException();
    }
}
