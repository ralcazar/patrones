package com.ejemplo.app.business.sagas.aplicacion.servicio.comun;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.ejemplo.app.business.ordermanager.dominio.ExternalId;
import com.ejemplo.app.business.sagas.aplicacion.puerto.entrada.CasoUsoIniciarTramitacion.ComandoIniciarTramitacion;
import com.ejemplo.app.business.sagas.dominio.sagaprincipal.DatoNegocio2;
import com.ejemplo.app.business.sagas.dominio.sagaprincipal.DatoNegocio3;
import com.ejemplo.app.business.sagas.dominio.sagaprincipal.EstadoSagaPrincipal;
import com.ejemplo.app.business.sagas.dominio.sagaprincipal.SagaPrincipal;
import com.ejemplo.app.testsoporte.RepositorioOrdenEnMemoria;

/** Iniciar una tramitación: crea el agregado (orden + saga principal) listo para ejecutarse. */
class ServicioIniciarTramitacionTest {

    private RepositorioOrdenEnMemoria repo;
    private ServicioIniciarTramitacion servicio;

    @BeforeEach
    void init() {
        repo = new RepositorioOrdenEnMemoria();
        servicio = new ServicioIniciarTramitacion(repo);
    }

    @Test
    void iniciar_creaLaOrdenConLaSagaPrincipalEnInicialYListaParaEjecutarseYa() {
        var cmd = new ComandoIniciarTramitacion(ExternalId.de(UUID.randomUUID().toString()),
                new DatoNegocio3("v1", "v2"), new DatoNegocio2("v1", "v2"));

        var antes = Instant.now();
        var sagaId = servicio.iniciar(cmd);
        var despues = Instant.now();

        var orden = repo.estadoActual(sagaId);
        assertThat(orden).isNotNull();
        assertThat(((SagaPrincipal) orden.proceso()).estado()).isEqualTo(EstadoSagaPrincipal.INICIAL);
        assertThat(orden.proximoReintentoEn()).isBetween(antes, despues);
    }
}
