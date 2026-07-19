package com.ejemplo.app.business.sagas.dominio.sagaprincipal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.UUID;

import org.junit.jupiter.api.Test;

import java.util.Map;

import com.ejemplo.app.business.sagas.dominio.comun.ContextoArranque;
import com.ejemplo.app.business.sagas.dominio.datosnegocio.DatosNegocioId;
import com.ejemplo.app.business.ordermanager.dominio.DatosManualesRequeridosException;
import com.ejemplo.app.business.ordermanager.dominio.ExternalId;
import com.ejemplo.app.business.ordermanager.dominio.PasoNoIntervenibleException;
import com.ejemplo.app.business.sagas.dominio.sagaprincipal.PuntoNoRetornoSuperadoException;
import com.ejemplo.app.business.sagas.dominio.comun.RefPaso1;
import com.ejemplo.app.business.sagas.dominio.comun.RefPaso5;
import com.ejemplo.app.business.sagas.dominio.comun.RefPaso7;
import com.ejemplo.app.business.sagas.dominio.sagasecundaria1.RefInicio;
import com.ejemplo.app.business.sagas.dominio.sagasecundaria1.ResultadoPasoSecundaria1;
import com.ejemplo.app.business.ordermanager.dominio.OrdenId;
import com.ejemplo.app.business.ordermanager.dominio.OrdenYaCompletadaException;
import com.ejemplo.app.business.ordermanager.dominio.UsuarioSoporte;

/**
 * Saga principal: PASO1..PASO8 síncronos, punto de no retorno en PASO7_HECHO
 * y compensación COMPENSAR_PASO2 -&gt; COMPENSAR_PASO1 -&gt; CANCELADA.
 *
 * {@code SagaPrincipal} es un value object inmutable: cada transición
 * devuelve una instancia nueva, así que los tests reasignan la variable
 * local tras cada llamada en vez de mutar "in place".
 */
class SagaPrincipalTest {

    private static SagaPrincipal nueva() {
        return SagaPrincipal.crear(OrdenId.nuevo(), ExternalId.de(UUID.randomUUID().toString()),
                DatosNegocioId.nuevo());
    }

    /** Ejecuta el flujo feliz completo, paso a paso, aplicando el resultado que produciría cada REST. */
    private static SagaPrincipal avanzarHastaTerminada(SagaPrincipal saga) {
        saga = saga.aplicarYAvanzar(new ResultadoPasoPrincipal.ResultadoPaso1(new RefPaso1("ref1")));
        saga = saga.aplicarYAvanzar(new ResultadoPasoPrincipal.ResultadoPaso2(new RefPaso2("ref2")));
        saga = saga.aplicarYAvanzar(new ResultadoPasoPrincipal.ResultadoPaso3(new RefPaso3("ref3")));
        saga = saga.aplicarYAvanzar(new ResultadoPasoPrincipal.ResultadoPaso4(new RefPaso4("ref4")));
        saga = saga.aplicarYAvanzar(new ResultadoPasoPrincipal.ResultadoPaso5(new RefPaso5("ref5")));
        saga = saga.aplicarYAvanzar(new ResultadoPasoPrincipal.ResultadoPaso6(new RefPaso6("ref6")));
        saga = saga.aplicarYAvanzar(new ResultadoPasoPrincipal.ResultadoPaso7(new RefPaso7("ref7")));
        saga = saga.aplicarYAvanzar(new ResultadoPasoPrincipal.ResultadoPaso8(new RefPaso8("ref8")));
        return saga;
    }

    @Test
    void flujoFeliz_recorreLosOchoPasosHastaTerminadaConResultadoOk() {
        var saga = nueva();
        assertThat(saga.estado()).isEqualTo(EstadoSagaPrincipal.INICIAL);

        saga = avanzarHastaTerminada(saga);

        assertThat(saga.estado()).isEqualTo(EstadoSagaPrincipal.TERMINADA);
        assertThat(saga.terminada()).isTrue();
    }

    @Test
    void flujoFeliz_alTerminarProduceElContextoDeArranqueDeLasTresSecundarias() {
        var saga = nueva();
        saga = avanzarHastaTerminada(saga);

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
        saga = avanzarHastaTerminada(saga);

        assertThatThrownBy(saga::comandoActual).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void comandoActual_enInicial_esEjecutarPaso1ConElExternalIdYElDatosNegocioIdDelContexto() {
        var saga = nueva();

        var comando = (ComandoPasoPrincipal.EjecutarPaso1) saga.comandoActual();

        assertThat(comando.externalId()).isEqualTo(saga.externalId());
        assertThat(comando.datosNegocioId()).isEqualTo(saga.contexto().datosNegocioId());
    }

    @Test
    void comandoActual_enPaso1Hecho_esEjecutarPaso2ConElDatosNegocioIdYLaRefPaso1() {
        var saga = nueva();
        saga = saga.aplicarYAvanzar(new ResultadoPasoPrincipal.ResultadoPaso1(new RefPaso1("ref1")));

        var comando = (ComandoPasoPrincipal.EjecutarPaso2) saga.comandoActual();

        assertThat(comando.datosNegocioId()).isEqualTo(saga.contexto().datosNegocioId());
        assertThat(comando.refPaso1()).isEqualTo(new RefPaso1("ref1"));
    }

    @Test
    void comandoActual_enPaso6Hecho_esEjecutarPaso7ConLaRefPaso5YElDatosNegocioId() {
        var saga = nueva();
        saga = saga.aplicarYAvanzar(new ResultadoPasoPrincipal.ResultadoPaso1(new RefPaso1("ref1")));
        saga = saga.aplicarYAvanzar(new ResultadoPasoPrincipal.ResultadoPaso2(new RefPaso2("ref2")));
        saga = saga.aplicarYAvanzar(new ResultadoPasoPrincipal.ResultadoPaso3(new RefPaso3("ref3")));
        saga = saga.aplicarYAvanzar(new ResultadoPasoPrincipal.ResultadoPaso4(new RefPaso4("ref4")));
        saga = saga.aplicarYAvanzar(new ResultadoPasoPrincipal.ResultadoPaso5(new RefPaso5("ref5")));
        saga = saga.aplicarYAvanzar(new ResultadoPasoPrincipal.ResultadoPaso6(new RefPaso6("ref6")));

        var comando = (ComandoPasoPrincipal.EjecutarPaso7) saga.comandoActual();

        assertThat(comando.refPaso5()).isEqualTo(new RefPaso5("ref5"));
        assertThat(comando.datosNegocioId()).isEqualTo(saga.contexto().datosNegocioId());
    }

    @Test
    void cancelar_antesDePaso1Hecho_vaDirectaACancelada() {
        var saga = nueva();

        saga = saga.cancelar(new com.ejemplo.app.business.ordermanager.dominio.UsuarioSoporte("ana"), "motivo");

        assertThat(saga.estado()).isEqualTo(EstadoSagaPrincipal.CANCELADA);
    }

    @Test
    void cancelar_conPaso1YPaso2HechosEncadenaCompensarPaso2LuegoPaso1LuegoCancelada() {
        var saga = nueva();
        saga = saga.aplicarYAvanzar(new ResultadoPasoPrincipal.ResultadoPaso1(new RefPaso1("ref1")));
        saga = saga.aplicarYAvanzar(new ResultadoPasoPrincipal.ResultadoPaso2(new RefPaso2("ref2")));
        assertThat(saga.estado()).isEqualTo(EstadoSagaPrincipal.PASO2_HECHO);

        saga = saga.cancelar(new com.ejemplo.app.business.ordermanager.dominio.UsuarioSoporte("ana"), "motivo");
        assertThat(saga.estado()).isEqualTo(EstadoSagaPrincipal.COMPENSAR_PASO2);

        saga = saga.compensacionCompletada();
        assertThat(saga.estado()).isEqualTo(EstadoSagaPrincipal.COMPENSAR_PASO1);

        saga = saga.compensacionCompletada();
        assertThat(saga.estado()).isEqualTo(EstadoSagaPrincipal.CANCELADA);
        assertThat(saga.terminada()).isTrue();
    }

    @Test
    void cancelar_conSoloPaso1Hecho_vaDirectaACompensarPaso1() {
        var saga = nueva();
        saga = saga.aplicarYAvanzar(new ResultadoPasoPrincipal.ResultadoPaso1(new RefPaso1("ref1")));

        saga = saga.cancelar(new com.ejemplo.app.business.ordermanager.dominio.UsuarioSoporte("ana"), "motivo");

        assertThat(saga.estado()).isEqualTo(EstadoSagaPrincipal.COMPENSAR_PASO1);
    }

    @Test
    void cancelar_esIdempotenteMientrasSeEstaCompensando() {
        var saga = nueva();
        saga = saga.aplicarYAvanzar(new ResultadoPasoPrincipal.ResultadoPaso1(new RefPaso1("ref1")));
        var quien = new com.ejemplo.app.business.ordermanager.dominio.UsuarioSoporte("ana");
        saga = saga.cancelar(quien, "motivo");
        assertThat(saga.estado()).isEqualTo(EstadoSagaPrincipal.COMPENSAR_PASO1);

        var repetida = saga.cancelar(quien, "motivo otra vez");

        assertThat(repetida.estado()).isEqualTo(EstadoSagaPrincipal.COMPENSAR_PASO1);
        assertThat(repetida).isSameAs(saga);
    }

    @Test
    void cancelar_enTerminadaLanzaSagaYaCompletada() {
        var saga = nueva();
        saga = avanzarHastaTerminada(saga);
        var terminada = saga;

        assertThatThrownBy(() -> terminada.cancelar(
                new com.ejemplo.app.business.ordermanager.dominio.UsuarioSoporte("ana"), "motivo"))
                .isInstanceOf(OrdenYaCompletadaException.class);
    }

    @Test
    void cancelar_enPaso7Hecho_puntoDeNoRetornoSuperado() {
        var saga = nueva();
        saga = saga.aplicarYAvanzar(new ResultadoPasoPrincipal.ResultadoPaso1(new RefPaso1("ref1")));
        saga = saga.aplicarYAvanzar(new ResultadoPasoPrincipal.ResultadoPaso2(new RefPaso2("ref2")));
        saga = saga.aplicarYAvanzar(new ResultadoPasoPrincipal.ResultadoPaso3(new RefPaso3("ref3")));
        saga = saga.aplicarYAvanzar(new ResultadoPasoPrincipal.ResultadoPaso4(new RefPaso4("ref4")));
        saga = saga.aplicarYAvanzar(new ResultadoPasoPrincipal.ResultadoPaso5(new RefPaso5("ref5")));
        saga = saga.aplicarYAvanzar(new ResultadoPasoPrincipal.ResultadoPaso6(new RefPaso6("ref6")));
        saga = saga.aplicarYAvanzar(new ResultadoPasoPrincipal.ResultadoPaso7(new RefPaso7("ref7")));
        assertThat(saga.estado()).isEqualTo(EstadoSagaPrincipal.PASO7_HECHO);
        assertThat(saga.esCancelable()).isFalse();
        var enPaso7Hecho = saga;

        assertThatThrownBy(() -> enPaso7Hecho.cancelar(
                new com.ejemplo.app.business.ordermanager.dominio.UsuarioSoporte("ana"), "motivo"))
                .isInstanceOf(PuntoNoRetornoSuperadoException.class);
    }

    @Test
    void aplicarYAvanzar_conResultadoDeOtraSaga_lanzaIllegalArgumentException() {
        var saga = nueva();

        assertThatThrownBy(() -> saga.aplicarYAvanzar(new ResultadoPasoSecundaria1.Iniciada(new RefInicio("x"))))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void aplicarYAvanzar_enEstadoTerminal_propagaIllegalStateExceptionDeAvanzar() {
        var saga = nueva();
        saga = avanzarHastaTerminada(saga);
        var terminada = saga;

        assertThatThrownBy(() -> terminada.aplicarYAvanzar(new ResultadoPasoPrincipal.ResultadoPaso1(new RefPaso1("ref1"))))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void marcarPasoActualOkManual_enInicialConDatos_aplicaResultadoYAvanzaAPaso1() {
        var saga = nueva();
        var quien = new UsuarioSoporte("ana");

        saga = saga.marcarPasoActualOkManual(quien, "justificacion", Map.of("refPaso1", "ref1manual"));

        assertThat(saga.estado()).isEqualTo(EstadoSagaPrincipal.PASO1_HECHO);
        assertThat(saga.contexto().refPaso1().valor()).isEqualTo("ref1manual");
    }

    @Test
    void marcarPasoActualOkManual_enPaso1HechoConDatos_aplicaResultadoYAvanzaAPaso2() {
        var saga = nueva();
        var quien = new UsuarioSoporte("ana");
        saga = saga.aplicarYAvanzar(new ResultadoPasoPrincipal.ResultadoPaso1(new RefPaso1("ref1")));

        saga = saga.marcarPasoActualOkManual(quien, "justificacion", Map.of("refPaso2", "ref2manual"));

        assertThat(saga.estado()).isEqualTo(EstadoSagaPrincipal.PASO2_HECHO);
        assertThat(saga.contexto().refPaso2().valor()).isEqualTo("ref2manual");
    }

    @Test
    void marcarPasoActualOkManual_enPaso3HechoConDatos_aplicaResultadoYAvanzaAPaso4() {
        var saga = nueva();
        var quien = new UsuarioSoporte("ana");
        saga = saga.aplicarYAvanzar(new ResultadoPasoPrincipal.ResultadoPaso1(new RefPaso1("ref1")));
        saga = saga.aplicarYAvanzar(new ResultadoPasoPrincipal.ResultadoPaso2(new RefPaso2("ref2")));
        saga = saga.aplicarYAvanzar(new ResultadoPasoPrincipal.ResultadoPaso3(new RefPaso3("ref3")));

        saga = saga.marcarPasoActualOkManual(quien, "justificacion", Map.of("refPaso4", "ref4manual"));

        assertThat(saga.estado()).isEqualTo(EstadoSagaPrincipal.PASO4_HECHO);
        assertThat(saga.contexto().refPaso4().valor()).isEqualTo("ref4manual");
    }

    @Test
    void marcarPasoActualOkManual_enPaso4HechoConDatos_aplicaResultadoYAvanzaAPaso5() {
        var saga = nueva();
        var quien = new UsuarioSoporte("ana");
        saga = saga.aplicarYAvanzar(new ResultadoPasoPrincipal.ResultadoPaso1(new RefPaso1("ref1")));
        saga = saga.aplicarYAvanzar(new ResultadoPasoPrincipal.ResultadoPaso2(new RefPaso2("ref2")));
        saga = saga.aplicarYAvanzar(new ResultadoPasoPrincipal.ResultadoPaso3(new RefPaso3("ref3")));
        saga = saga.aplicarYAvanzar(new ResultadoPasoPrincipal.ResultadoPaso4(new RefPaso4("ref4")));

        saga = saga.marcarPasoActualOkManual(quien, "justificacion", Map.of("refPaso5", "ref5manual"));

        assertThat(saga.estado()).isEqualTo(EstadoSagaPrincipal.PASO5_HECHO);
        assertThat(saga.contexto().refPaso5().valor()).isEqualTo("ref5manual");
    }

    @Test
    void marcarPasoActualOkManual_enPaso6HechoConDatos_aplicaResultadoYAvanzaAPaso7() {
        var saga = nueva();
        var quien = new UsuarioSoporte("ana");
        saga = saga.aplicarYAvanzar(new ResultadoPasoPrincipal.ResultadoPaso1(new RefPaso1("ref1")));
        saga = saga.aplicarYAvanzar(new ResultadoPasoPrincipal.ResultadoPaso2(new RefPaso2("ref2")));
        saga = saga.aplicarYAvanzar(new ResultadoPasoPrincipal.ResultadoPaso3(new RefPaso3("ref3")));
        saga = saga.aplicarYAvanzar(new ResultadoPasoPrincipal.ResultadoPaso4(new RefPaso4("ref4")));
        saga = saga.aplicarYAvanzar(new ResultadoPasoPrincipal.ResultadoPaso5(new RefPaso5("ref5")));
        saga = saga.aplicarYAvanzar(new ResultadoPasoPrincipal.ResultadoPaso6(new RefPaso6("ref6")));

        saga = saga.marcarPasoActualOkManual(quien, "justificacion", Map.of("refPaso7", "ref7manual"));

        assertThat(saga.estado()).isEqualTo(EstadoSagaPrincipal.PASO7_HECHO);
        assertThat(saga.contexto().refPaso7().valor()).isEqualTo("ref7manual");
    }

    @Test
    void marcarPasoActualOkManual_conDatosNulos_enEstadoQueLosRequiere_lanzaDatosManualesRequeridosException() {
        var saga = nueva();
        var quien = new UsuarioSoporte("ana");

        assertThatThrownBy(() -> saga.marcarPasoActualOkManual(quien, "justificacion", null))
                .isInstanceOf(DatosManualesRequeridosException.class);
    }

    @Test
    void marcarPasoActualOkManual_conDatosVacios_enEstadoQueLosRequiere_lanzaDatosManualesRequeridosException() {
        var saga = nueva();
        var quien = new UsuarioSoporte("ana");

        assertThatThrownBy(() -> saga.marcarPasoActualOkManual(quien, "justificacion", Map.of()))
                .isInstanceOf(DatosManualesRequeridosException.class);
    }

    @Test
    void marcarPasoActualOkManual_enEstadoQueNoRequiereDatos_avanzaSinAplicarResultado() {
        var saga = nueva();
        var quien = new UsuarioSoporte("ana");
        saga = saga.aplicarYAvanzar(new ResultadoPasoPrincipal.ResultadoPaso1(new RefPaso1("ref1")));
        saga = saga.aplicarYAvanzar(new ResultadoPasoPrincipal.ResultadoPaso2(new RefPaso2("ref2")));
        assertThat(saga.estado()).isEqualTo(EstadoSagaPrincipal.PASO2_HECHO);

        saga = saga.marcarPasoActualOkManual(quien, "justificacion", null);

        assertThat(saga.estado()).isEqualTo(EstadoSagaPrincipal.PASO3_HECHO);
        assertThat(saga.contexto().refPaso3()).isNull();
    }

    @Test
    void marcarPasoActualOkManual_enEstadoQueNoRequiereDatos_conDatosIgnoradosAvanzaSinAplicarResultado() {
        var saga = nueva();
        var quien = new UsuarioSoporte("ana");
        saga = saga.aplicarYAvanzar(new ResultadoPasoPrincipal.ResultadoPaso1(new RefPaso1("ref1")));
        saga = saga.aplicarYAvanzar(new ResultadoPasoPrincipal.ResultadoPaso2(new RefPaso2("ref2")));
        assertThat(saga.estado()).isEqualTo(EstadoSagaPrincipal.PASO2_HECHO);

        saga = saga.marcarPasoActualOkManual(quien, "justificacion", Map.of("dato", "ignorado"));

        assertThat(saga.estado()).isEqualTo(EstadoSagaPrincipal.PASO3_HECHO);
        assertThat(saga.contexto().refPaso3()).isNull();
    }

    @Test
    void marcarPasoActualOkManual_faltandoLaClaveObligatoria_lanzaIllegalArgumentException() {
        var saga = nueva();
        var quien = new UsuarioSoporte("ana");

        assertThatThrownBy(() -> saga.marcarPasoActualOkManual(quien, "justificacion", Map.of("otraClave", "x")))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void marcarPasoActualOkManual_enTerminada_lanzaPasoNoIntervenibleException() {
        var saga = nueva();
        saga = avanzarHastaTerminada(saga);
        var terminada = saga;
        var quien = new UsuarioSoporte("ana");

        assertThatThrownBy(() -> terminada.marcarPasoActualOkManual(quien, "justificacion", null))
                .isInstanceOf(PasoNoIntervenibleException.class);
    }

    @Test
    void compensacionCompletada_enEstadoNoDeCompensacion_lanzaIllegalStateException() {
        var saga = nueva();

        assertThatThrownBy(saga::compensacionCompletada).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void marcarPasoActualOkManual_conDatosVaciosEnEstadoQueNoLosRequiere_avanzaSinAplicarResultado() {
        var saga = nueva();
        var quien = new UsuarioSoporte("ana");
        saga = saga.aplicarYAvanzar(new ResultadoPasoPrincipal.ResultadoPaso1(new RefPaso1("ref1")));
        saga = saga.aplicarYAvanzar(new ResultadoPasoPrincipal.ResultadoPaso2(new RefPaso2("ref2")));
        assertThat(saga.estado()).isEqualTo(EstadoSagaPrincipal.PASO2_HECHO);

        saga = saga.marcarPasoActualOkManual(quien, "justificacion", Map.of());

        assertThat(saga.estado()).isEqualTo(EstadoSagaPrincipal.PASO3_HECHO);
    }

    @Test
    void marcarPasoActualOkManual_conValorEnBlancoEnLaClaveObligatoria_lanzaIllegalArgumentException() {
        var saga = nueva();
        var quien = new UsuarioSoporte("ana");

        assertThatThrownBy(() -> saga.marcarPasoActualOkManual(quien, "justificacion", Map.of("refPaso1", "  ")))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void esCancelable_antesDePaso7Hecho_esTrue() {
        var saga = nueva();

        assertThat(saga.esCancelable()).isTrue();
    }

    @Test
    void cancelar_esIdempotenteEnCompensarPaso2() {
        var saga = nueva();
        saga = saga.aplicarYAvanzar(new ResultadoPasoPrincipal.ResultadoPaso1(new RefPaso1("ref1")));
        saga = saga.aplicarYAvanzar(new ResultadoPasoPrincipal.ResultadoPaso2(new RefPaso2("ref2")));
        var quien = new UsuarioSoporte("ana");
        saga = saga.cancelar(quien, "motivo");
        assertThat(saga.estado()).isEqualTo(EstadoSagaPrincipal.COMPENSAR_PASO2);

        var repetida = saga.cancelar(quien, "motivo otra vez");

        assertThat(repetida.estado()).isEqualTo(EstadoSagaPrincipal.COMPENSAR_PASO2);
    }

    @Test
    void cancelar_esIdempotenteEnCancelada() {
        var saga = nueva();
        var quien = new UsuarioSoporte("ana");
        saga = saga.cancelar(quien, "motivo");
        assertThat(saga.estado()).isEqualTo(EstadoSagaPrincipal.CANCELADA);

        var repetida = saga.cancelar(quien, "motivo otra vez");

        assertThat(repetida.estado()).isEqualTo(EstadoSagaPrincipal.CANCELADA);
    }

    // ------------------------------------------------------------------
    // equals/hashCode: value object -- igualdad por (id, externalId, estado,
    // auditoria) heredados de Proceso, más ctx propio de SagaPrincipal.
    // ------------------------------------------------------------------

    @Test
    void equals_esReflexivo_mismaInstancia() {
        var saga = nueva();

        assertThat(saga.equals(saga)).isTrue();
    }

    @Test
    void equals_conProcesoDeOtroTipo_esFalse() {
        var id = OrdenId.nuevo();
        var externalId = ExternalId.de(UUID.randomUUID().toString());
        var principal = SagaPrincipal.rehidratar(id, externalId,
                com.ejemplo.app.business.sagas.dominio.sagaprincipal.ContextoTramitacion.inicial(DatosNegocioId.nuevo()),
                EstadoSagaPrincipal.INICIAL, java.util.List.of());
        var secundaria3 = com.ejemplo.app.business.sagas.dominio.sagasecundaria3.SagaSecundaria3.rehidratar(
                id, externalId, new RefPaso7("ref7"), null,
                com.ejemplo.app.business.sagas.dominio.sagasecundaria3.EstadoSagaSecundaria3.INICIAL, java.util.List.of());

        assertThat(principal.equals(secundaria3)).isFalse();
    }

    @Test
    void equals_conCtxDistinto_esFalse() {
        var id = OrdenId.nuevo();
        var externalId = ExternalId.de(UUID.randomUUID().toString());
        var uno = SagaPrincipal.rehidratar(id, externalId,
                ContextoTramitacion.inicial(DatosNegocioId.nuevo()), EstadoSagaPrincipal.INICIAL, java.util.List.of());
        var otro = SagaPrincipal.rehidratar(id, externalId,
                ContextoTramitacion.inicial(DatosNegocioId.nuevo()), EstadoSagaPrincipal.INICIAL, java.util.List.of());

        assertThat(uno.equals(otro)).isFalse();
    }

    @Test
    void equals_conTodosLosCamposIguales_esTrue() {
        var id = OrdenId.nuevo();
        var externalId = ExternalId.de(UUID.randomUUID().toString());
        var datosNegocioId = DatosNegocioId.nuevo();
        var uno = SagaPrincipal.rehidratar(id, externalId,
                ContextoTramitacion.inicial(datosNegocioId), EstadoSagaPrincipal.INICIAL, java.util.List.of());
        var otro = SagaPrincipal.rehidratar(id, externalId,
                ContextoTramitacion.inicial(datosNegocioId), EstadoSagaPrincipal.INICIAL, java.util.List.of());

        assertThat(uno).isEqualTo(otro);
        assertThat(uno.hashCode()).isEqualTo(otro.hashCode());
    }
}
