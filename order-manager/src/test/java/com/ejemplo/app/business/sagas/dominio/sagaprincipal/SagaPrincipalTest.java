package com.ejemplo.app.business.sagas.dominio.sagaprincipal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.UUID;

import org.junit.jupiter.api.Test;

import com.ejemplo.app.business.sagas.dominio.comun.ContextoArranque;
import com.ejemplo.app.business.ordermanager.dominio.ExternalId;
import com.ejemplo.app.business.sagas.dominio.sagaprincipal.PuntoNoRetornoSuperadoException;
import com.ejemplo.app.business.sagas.dominio.comun.RefPaso1;
import com.ejemplo.app.business.sagas.dominio.comun.RefPaso5;
import com.ejemplo.app.business.sagas.dominio.comun.RefPaso7;
import com.ejemplo.app.business.ordermanager.dominio.ResultadoOrden;
import com.ejemplo.app.business.ordermanager.dominio.OrdenId;
import com.ejemplo.app.business.ordermanager.dominio.OrdenYaCompletadaException;

/**
 * Saga principal: PASO1..PASO8 síncronos, punto de no retorno en PASO7_HECHO
 * y compensación COMPENSAR_PASO2 -&gt; COMPENSAR_PASO1 -&gt; CANCELADA.
 */
class SagaPrincipalTest {

    private static SagaPrincipal nueva() {
        return SagaPrincipal.crear(OrdenId.nuevo(), ExternalId.de(UUID.randomUUID().toString()),
                new DatoNegocio3("v1", "v2"), new DatoNegocio2("v1", "v2"));
    }

    /** Ejecuta el flujo feliz completo, paso a paso, aplicando el resultado que produciría cada REST. */
    private static void avanzarHastaTerminada(SagaPrincipal saga) {
        saga.aplicarYAvanzar(new ResultadoPasoPrincipal.ResultadoPaso1(new RefPaso1("ref1")));
        saga.aplicarYAvanzar(new ResultadoPasoPrincipal.ResultadoPaso2(new RefPaso2("ref2")));
        saga.aplicarYAvanzar(new ResultadoPasoPrincipal.ResultadoPaso3(new RefPaso3("ref3")));
        saga.aplicarYAvanzar(new ResultadoPasoPrincipal.ResultadoPaso4(new RefPaso4("ref4")));
        saga.aplicarYAvanzar(new ResultadoPasoPrincipal.ResultadoPaso5(new RefPaso5("ref5")));
        saga.aplicarYAvanzar(new ResultadoPasoPrincipal.ResultadoPaso6(new RefPaso6("ref6")));
        saga.aplicarYAvanzar(new ResultadoPasoPrincipal.ResultadoPaso7(new RefPaso7("ref7")));
        saga.aplicarYAvanzar(new ResultadoPasoPrincipal.ResultadoPaso8(new RefPaso8("ref8")));
    }

    @Test
    void flujoFeliz_recorreLosOchoPasosHastaTerminadaConResultadoOk() {
        var saga = nueva();
        assertThat(saga.estado()).isEqualTo(EstadoSagaPrincipal.INICIAL);

        avanzarHastaTerminada(saga);

        assertThat(saga.estado()).isEqualTo(EstadoSagaPrincipal.TERMINADA);
        assertThat(saga.terminada()).isTrue();
        assertThat(saga.resultadoFinal()).isEqualTo(ResultadoOrden.FINALIZADA_OK);
    }

    @Test
    void flujoFeliz_alTerminarProduceElContextoDeArranqueDeLasTresSecundarias() {
        var saga = nueva();
        avanzarHastaTerminada(saga);

        var contextos = saga.contextosArranque();

        assertThat(contextos).hasSize(3);
        assertThat(contextos).filteredOn(ContextoArranque.ArranqueSecundaria1.class::isInstance)
                .singleElement()
                .satisfies(c -> assertThat(((ContextoArranque.ArranqueSecundaria1) c).refPaso1().valor())
                        .isEqualTo("ref1"));
        assertThat(contextos).filteredOn(ContextoArranque.ArranqueSecundaria2.class::isInstance)
                .singleElement()
                .satisfies(c -> assertThat(((ContextoArranque.ArranqueSecundaria2) c).refPaso5().valor())
                        .isEqualTo("ref5"));
        assertThat(contextos).filteredOn(ContextoArranque.ArranqueSecundaria3.class::isInstance)
                .singleElement()
                .satisfies(c -> assertThat(((ContextoArranque.ArranqueSecundaria3) c).refPaso7().valor())
                        .isEqualTo("ref7"));
    }

    @Test
    void comandoActual_enTerminadaNoTienePasoPendiente() {
        var saga = nueva();
        avanzarHastaTerminada(saga);

        assertThatThrownBy(saga::comandoActual).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void cancelar_antesDePaso1Hecho_vaDirectaACancelada() {
        var saga = nueva();

        saga.cancelar(new com.ejemplo.app.business.ordermanager.dominio.UsuarioSoporte("ana"), "motivo");

        assertThat(saga.estado()).isEqualTo(EstadoSagaPrincipal.CANCELADA);
    }

    @Test
    void cancelar_conPaso1YPaso2HechosEncadenaCompensarPaso2LuegoPaso1LuegoCancelada() {
        var saga = nueva();
        saga.aplicarYAvanzar(new ResultadoPasoPrincipal.ResultadoPaso1(new RefPaso1("ref1")));
        saga.aplicarYAvanzar(new ResultadoPasoPrincipal.ResultadoPaso2(new RefPaso2("ref2")));
        assertThat(saga.estado()).isEqualTo(EstadoSagaPrincipal.PASO2_HECHO);

        saga.cancelar(new com.ejemplo.app.business.ordermanager.dominio.UsuarioSoporte("ana"), "motivo");
        assertThat(saga.estado()).isEqualTo(EstadoSagaPrincipal.COMPENSAR_PASO2);

        saga.compensacionCompletada();
        assertThat(saga.estado()).isEqualTo(EstadoSagaPrincipal.COMPENSAR_PASO1);

        saga.compensacionCompletada();
        assertThat(saga.estado()).isEqualTo(EstadoSagaPrincipal.CANCELADA);
        assertThat(saga.terminada()).isTrue();
        assertThat(saga.resultadoFinal()).isEqualTo(ResultadoOrden.FINALIZADA_COMPENSADA);
    }

    @Test
    void cancelar_conSoloPaso1Hecho_vaDirectaACompensarPaso1() {
        var saga = nueva();
        saga.aplicarYAvanzar(new ResultadoPasoPrincipal.ResultadoPaso1(new RefPaso1("ref1")));

        saga.cancelar(new com.ejemplo.app.business.ordermanager.dominio.UsuarioSoporte("ana"), "motivo");

        assertThat(saga.estado()).isEqualTo(EstadoSagaPrincipal.COMPENSAR_PASO1);
    }

    @Test
    void cancelar_esIdempotenteMientrasSeEstaCompensando() {
        var saga = nueva();
        saga.aplicarYAvanzar(new ResultadoPasoPrincipal.ResultadoPaso1(new RefPaso1("ref1")));
        var quien = new com.ejemplo.app.business.ordermanager.dominio.UsuarioSoporte("ana");
        saga.cancelar(quien, "motivo");
        assertThat(saga.estado()).isEqualTo(EstadoSagaPrincipal.COMPENSAR_PASO1);

        saga.cancelar(quien, "motivo otra vez");

        assertThat(saga.estado()).isEqualTo(EstadoSagaPrincipal.COMPENSAR_PASO1);
    }

    @Test
    void cancelar_enTerminadaLanzaSagaYaCompletada() {
        var saga = nueva();
        avanzarHastaTerminada(saga);

        assertThatThrownBy(() -> saga.cancelar(
                new com.ejemplo.app.business.ordermanager.dominio.UsuarioSoporte("ana"), "motivo"))
                .isInstanceOf(OrdenYaCompletadaException.class);
    }

    @Test
    void cancelar_enPaso7Hecho_puntoDeNoRetornoSuperado() {
        var saga = nueva();
        saga.aplicarYAvanzar(new ResultadoPasoPrincipal.ResultadoPaso1(new RefPaso1("ref1")));
        saga.aplicarYAvanzar(new ResultadoPasoPrincipal.ResultadoPaso2(new RefPaso2("ref2")));
        saga.aplicarYAvanzar(new ResultadoPasoPrincipal.ResultadoPaso3(new RefPaso3("ref3")));
        saga.aplicarYAvanzar(new ResultadoPasoPrincipal.ResultadoPaso4(new RefPaso4("ref4")));
        saga.aplicarYAvanzar(new ResultadoPasoPrincipal.ResultadoPaso5(new RefPaso5("ref5")));
        saga.aplicarYAvanzar(new ResultadoPasoPrincipal.ResultadoPaso6(new RefPaso6("ref6")));
        saga.aplicarYAvanzar(new ResultadoPasoPrincipal.ResultadoPaso7(new RefPaso7("ref7")));
        assertThat(saga.estado()).isEqualTo(EstadoSagaPrincipal.PASO7_HECHO);
        assertThat(saga.esCancelable()).isFalse();

        assertThatThrownBy(() -> saga.cancelar(
                new com.ejemplo.app.business.ordermanager.dominio.UsuarioSoporte("ana"), "motivo"))
                .isInstanceOf(PuntoNoRetornoSuperadoException.class);
    }
}
