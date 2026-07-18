package com.ejemplo.app.infraestructure.sagas.persistencia;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import com.ejemplo.app.business.ordermanager.dominio.ExternalId;
import com.ejemplo.app.business.ordermanager.dominio.OrdenId;
import com.ejemplo.app.business.sagas.dominio.sagaprincipal.DatoNegocio2;
import com.ejemplo.app.business.sagas.dominio.sagaprincipal.DatoNegocio3;
import com.ejemplo.app.business.sagas.dominio.sagaprincipal.EstadoSagaPrincipal;
import com.ejemplo.app.business.sagas.dominio.sagaprincipal.ResultadoPasoPrincipal;
import com.ejemplo.app.business.sagas.dominio.sagaprincipal.SagaPrincipal;
import com.ejemplo.app.business.sagas.dominio.comun.RefPaso1;
import com.ejemplo.app.business.sagas.dominio.comun.RefPaso5;
import com.ejemplo.app.business.sagas.dominio.comun.RefPaso7;
import com.ejemplo.app.infraestructure.ordermanager.persistencia.MapeadorProceso;

/** {@link MapeadorProceso}/{@link com.ejemplo.app.infraestructure.ordermanager.persistencia.DescriptorSoporteOrden} de la saga principal. */
class SoporteSagaPrincipalTest {

    private final SoporteSagaPrincipal soporte = new SoporteSagaPrincipal();

    @Test
    void tipo_esPrincipal() {
        assertThat(soporte.tipo()).isEqualTo(SagaPrincipal.TIPO);
    }

    @Test
    void pasoPendiente_recorreLosOchoEstadosDelFlujoNormal() {
        assertThat(soporte.pasoPendiente("INICIAL")).isEqualTo("PASO1");
        assertThat(soporte.pasoPendiente("PASO1_HECHO")).isEqualTo("PASO2");
        assertThat(soporte.pasoPendiente("PASO2_HECHO")).isEqualTo("PASO3");
        assertThat(soporte.pasoPendiente("PASO3_HECHO")).isEqualTo("PASO4");
        assertThat(soporte.pasoPendiente("PASO4_HECHO")).isEqualTo("PASO5");
        assertThat(soporte.pasoPendiente("PASO5_HECHO")).isEqualTo("PASO6");
        assertThat(soporte.pasoPendiente("PASO6_HECHO")).isEqualTo("PASO7");
        assertThat(soporte.pasoPendiente("PASO7_HECHO")).isEqualTo("PASO8");
    }

    @Test
    void pasoPendiente_enEstadoFinalDevuelveNull() {
        assertThat(soporte.pasoPendiente("TERMINADA")).isNull();
        assertThat(soporte.pasoPendiente("CANCELADA")).isNull();
    }

    @Test
    void datosManualesObligatorios_soloEnLosPasosQueConsumenDatosAportados() {
        assertThat(soporte.datosManualesObligatorios("INICIAL")).isTrue();
        assertThat(soporte.datosManualesObligatorios("PASO1_HECHO")).isTrue();
        assertThat(soporte.datosManualesObligatorios("PASO3_HECHO")).isTrue();
        assertThat(soporte.datosManualesObligatorios("PASO4_HECHO")).isTrue();
        assertThat(soporte.datosManualesObligatorios("PASO6_HECHO")).isTrue();
        assertThat(soporte.datosManualesObligatorios("PASO2_HECHO")).isFalse();
        assertThat(soporte.datosManualesObligatorios("TERMINADA")).isFalse();
    }

    @Test
    void cancelable_hastaPaso6HechoIncluido() {
        assertThat(soporte.cancelable("INICIAL")).isTrue();
        assertThat(soporte.cancelable("PASO6_HECHO")).isTrue();
        assertThat(soporte.cancelable("PASO7_HECHO")).isFalse();
        assertThat(soporte.cancelable("TERMINADA")).isFalse();
    }

    @Test
    void desarmarYRearmar_conContextoInicialSinNingunaRefHaceIdaYVuelta() {
        var id = OrdenId.nuevo();
        var externalId = ExternalId.de(UUID.randomUUID().toString());
        var saga = SagaPrincipal.crear(id, externalId, new DatoNegocio3("v1", "v2"), new DatoNegocio2("v3", "v4"));

        var persistible = soporte.desarmar(saga);
        var rearmada = (SagaPrincipal) soporte.rearmar(id, externalId, persistible.estado(),
                persistible.contexto(), List.of());

        assertThat(persistible.estado()).isEqualTo("INICIAL");
        assertThat(persistible.contexto()).doesNotContainKeys("refPaso1", "refPaso2");
        assertThat(rearmada.estado()).isEqualTo(EstadoSagaPrincipal.INICIAL);
        assertThat(rearmada.contexto()).isEqualTo(saga.contexto());
    }

    @Test
    void desarmarYRearmar_conTodasLasRefsRellenasHaceIdaYVuelta() {
        var id = OrdenId.nuevo();
        var externalId = ExternalId.de(UUID.randomUUID().toString());
        var saga = SagaPrincipal.crear(id, externalId, new DatoNegocio3("v1", "v2"), new DatoNegocio2("v3", "v4"));
        saga.aplicarYAvanzar(new ResultadoPasoPrincipal.ResultadoPaso1(new RefPaso1("ref1")));
        saga.aplicarYAvanzar(new ResultadoPasoPrincipal.ResultadoPaso2(
                new com.ejemplo.app.business.sagas.dominio.sagaprincipal.RefPaso2("ref2")));
        saga.aplicarYAvanzar(new ResultadoPasoPrincipal.ResultadoPaso3(
                new com.ejemplo.app.business.sagas.dominio.sagaprincipal.RefPaso3("ref3")));
        saga.aplicarYAvanzar(new ResultadoPasoPrincipal.ResultadoPaso4(
                new com.ejemplo.app.business.sagas.dominio.sagaprincipal.RefPaso4("ref4")));
        saga.aplicarYAvanzar(new ResultadoPasoPrincipal.ResultadoPaso5(new RefPaso5("ref5")));
        saga.aplicarYAvanzar(new ResultadoPasoPrincipal.ResultadoPaso6(
                new com.ejemplo.app.business.sagas.dominio.sagaprincipal.RefPaso6("ref6")));
        saga.aplicarYAvanzar(new ResultadoPasoPrincipal.ResultadoPaso7(new RefPaso7("ref7")));
        saga.aplicarYAvanzar(new ResultadoPasoPrincipal.ResultadoPaso8(
                new com.ejemplo.app.business.sagas.dominio.sagaprincipal.RefPaso8("ref8")));

        var persistible = soporte.desarmar(saga);
        var rearmada = (SagaPrincipal) soporte.rearmar(id, externalId, persistible.estado(),
                persistible.contexto(), List.of());

        assertThat(persistible.estado()).isEqualTo("TERMINADA");
        assertThat(persistible.contexto())
                .containsEntry("refPaso1", "ref1")
                .containsEntry("refPaso8", "ref8");
        assertThat(rearmada.estado()).isEqualTo(EstadoSagaPrincipal.TERMINADA);
        assertThat(rearmada.contexto()).isEqualTo(saga.contexto());
    }
}
