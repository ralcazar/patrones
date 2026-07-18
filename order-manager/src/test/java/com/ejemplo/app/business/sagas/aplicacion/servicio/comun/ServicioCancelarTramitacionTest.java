package com.ejemplo.app.business.sagas.aplicacion.servicio.comun;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.ejemplo.app.business.ordermanager.dominio.ExternalId;
import com.ejemplo.app.business.ordermanager.dominio.OrdenId;
import com.ejemplo.app.business.ordermanager.dominio.OrdenRoot;
import com.ejemplo.app.business.ordermanager.dominio.OrdenYaCompletadaException;
import com.ejemplo.app.business.ordermanager.dominio.UsuarioSoporte;
import com.ejemplo.app.business.sagas.dominio.comun.RefPaso1;
import com.ejemplo.app.business.sagas.dominio.comun.RefPaso5;
import com.ejemplo.app.business.sagas.dominio.comun.RefPaso7;
import com.ejemplo.app.business.sagas.dominio.sagaprincipal.DatoNegocio2;
import com.ejemplo.app.business.sagas.dominio.sagaprincipal.DatoNegocio3;
import com.ejemplo.app.business.sagas.dominio.sagaprincipal.EstadoSagaPrincipal;
import com.ejemplo.app.business.sagas.dominio.sagaprincipal.RefPaso2;
import com.ejemplo.app.business.sagas.dominio.sagaprincipal.RefPaso3;
import com.ejemplo.app.business.sagas.dominio.sagaprincipal.RefPaso4;
import com.ejemplo.app.business.sagas.dominio.sagaprincipal.RefPaso6;
import com.ejemplo.app.business.sagas.dominio.sagaprincipal.RefPaso8;
import com.ejemplo.app.business.sagas.dominio.sagaprincipal.ResultadoPasoPrincipal;
import com.ejemplo.app.business.sagas.dominio.sagaprincipal.SagaPrincipal;
import com.ejemplo.app.testsoporte.RepositorioOrdenEnMemoria;

/** Cancelación de la saga principal: actúa directamente sobre el agregado vía RepositorioOrden. */
class ServicioCancelarTramitacionTest {

    private RepositorioOrdenEnMemoria repo;
    private ServicioCancelarTramitacion servicio;

    @BeforeEach
    void init() {
        repo = new RepositorioOrdenEnMemoria();
        servicio = new ServicioCancelarTramitacion(repo);
    }

    private OrdenId crearTramitacion() {
        var id = OrdenId.nuevo();
        var saga = SagaPrincipal.crear(id, ExternalId.de(UUID.randomUUID().toString()),
                new DatoNegocio3("v1", "v2"), new DatoNegocio2("v1", "v2"));
        var orden = OrdenRoot.nueva(saga, Instant.now());
        orden.aparcar(java.time.Duration.ofDays(1), Instant.now());
        repo.crear(orden);
        return id;
    }

    @Test
    void cancelarPrincipal_antesDelPuntoDeNoRetorno_cancelaYDespiertaLaOrden() {
        var id = crearTramitacion();

        servicio.cancelarPrincipal(id, new UsuarioSoporte("ana"), "motivo");

        var orden = repo.estadoActual(id);
        assertThat(((SagaPrincipal) orden.proceso()).estado()).isEqualTo(EstadoSagaPrincipal.CANCELADA);
        assertThat(orden.proximoReintentoEn()).isBeforeOrEqualTo(Instant.now());
    }

    @Test
    void cancelarPrincipal_enTerminada_propagaOrdenYaCompletadaException() {
        var id = crearTramitacion();
        var orden = repo.cargar(id);
        var saga = (SagaPrincipal) orden.proceso();
        saga.aplicarYAvanzar(new ResultadoPasoPrincipal.ResultadoPaso1(new RefPaso1("ref1")));
        saga.aplicarYAvanzar(new ResultadoPasoPrincipal.ResultadoPaso2(new RefPaso2("ref2")));
        saga.aplicarYAvanzar(new ResultadoPasoPrincipal.ResultadoPaso3(new RefPaso3("ref3")));
        saga.aplicarYAvanzar(new ResultadoPasoPrincipal.ResultadoPaso4(new RefPaso4("ref4")));
        saga.aplicarYAvanzar(new ResultadoPasoPrincipal.ResultadoPaso5(new RefPaso5("ref5")));
        saga.aplicarYAvanzar(new ResultadoPasoPrincipal.ResultadoPaso6(new RefPaso6("ref6")));
        saga.aplicarYAvanzar(new ResultadoPasoPrincipal.ResultadoPaso7(new RefPaso7("ref7")));
        saga.aplicarYAvanzar(new ResultadoPasoPrincipal.ResultadoPaso8(new RefPaso8("ref8")));
        repo.guardar(orden);

        assertThatThrownBy(() -> servicio.cancelarPrincipal(id, new UsuarioSoporte("ana"), "motivo"))
                .isInstanceOf(OrdenYaCompletadaException.class);
    }
}
