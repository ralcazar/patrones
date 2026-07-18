package com.ejemplo.app.infraestructure.sagas.persistencia;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import com.ejemplo.app.business.ordermanager.dominio.ExternalId;
import com.ejemplo.app.business.ordermanager.dominio.OrdenId;
import com.ejemplo.app.business.sagas.dominio.datosnegocio.DatosNegocioId;
import com.ejemplo.app.business.sagas.dominio.sagaprincipal.EstadoSagaPrincipal;
import com.ejemplo.app.business.sagas.dominio.sagaprincipal.ResultadoPasoPrincipal;
import com.ejemplo.app.business.sagas.dominio.sagaprincipal.SagaPrincipal;
import com.ejemplo.app.business.sagas.dominio.comun.RefPaso1;
import com.ejemplo.app.business.sagas.dominio.comun.RefPaso5;
import com.ejemplo.app.business.sagas.dominio.comun.RefPaso7;
import com.ejemplo.app.infraestructure.ordermanager.persistencia.MapeadorProceso;

/**
 * {@link MapeadorProceso}/{@link com.ejemplo.app.infraestructure.ordermanager.persistencia.DescriptorSoporteOrden}
 * de la saga principal. El repo JPA de la satélite (Spring Data) se dobla con
 * Mockito: esto SÍ es un test de infraestructure (no de business), así que
 * Mockito/Spring Data están permitidos aquí (ver CLAUDE.md,
 * {@code srcTestNoDependeDeSpring} solo prohíbe depender DIRECTAMENTE de
 * {@code org.springframework..}, no de un tipo de producción que a su vez lo haga).
 */
class SoporteSagaPrincipalTest {

    private final ProcesoSagaPrincipalJpaRepository repo = mock(ProcesoSagaPrincipalJpaRepository.class);
    private final SoporteSagaPrincipal soporte = new SoporteSagaPrincipal(repo);

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
    void estado_devuelveElNombreDelEstadoDeLaFsm() {
        var saga = SagaPrincipal.crear(OrdenId.nuevo(), ExternalId.de(UUID.randomUUID().toString()), DatosNegocioId.nuevo());

        assertThat(soporte.estado(saga)).isEqualTo("INICIAL");
    }

    @Test
    void guardarContexto_conContextoInicialSinNingunaRefGuardaLaEntidadConLosCamposANull() {
        var id = OrdenId.nuevo();
        var datosNegocioId = DatosNegocioId.nuevo();
        var saga = SagaPrincipal.crear(id, ExternalId.de(UUID.randomUUID().toString()), datosNegocioId);
        var captor = ArgumentCaptor.forClass(ProcesoSagaPrincipalEntity.class);

        soporte.guardarContexto(saga);

        verify(repo).save(captor.capture());
        var entidad = captor.getValue();
        assertThat(entidad.getOrdenId()).isEqualTo(id.valor());
        assertThat(entidad.getDatosnegocioId()).isEqualTo(datosNegocioId.valor());
        assertThat(entidad.getRefPaso1()).isNull();
        assertThat(entidad.getRefPaso8()).isNull();
    }

    @Test
    void guardarContexto_conTodasLasRefsRellenasGuardaLaEntidadCompleta() {
        var id = OrdenId.nuevo();
        var saga = SagaPrincipal.crear(id, ExternalId.de(UUID.randomUUID().toString()), DatosNegocioId.nuevo());
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
        var captor = ArgumentCaptor.forClass(ProcesoSagaPrincipalEntity.class);

        soporte.guardarContexto(saga);

        verify(repo).save(captor.capture());
        var entidad = captor.getValue();
        assertThat(entidad.getRefPaso1()).isEqualTo("ref1");
        assertThat(entidad.getRefPaso2()).isEqualTo("ref2");
        assertThat(entidad.getRefPaso3()).isEqualTo("ref3");
        assertThat(entidad.getRefPaso4()).isEqualTo("ref4");
        assertThat(entidad.getRefPaso5()).isEqualTo("ref5");
        assertThat(entidad.getRefPaso6()).isEqualTo("ref6");
        assertThat(entidad.getRefPaso7()).isEqualTo("ref7");
        assertThat(entidad.getRefPaso8()).isEqualTo("ref8");
        assertThat(soporte.estado(saga)).isEqualTo("TERMINADA");
    }

    @Test
    void rearmar_conEntidadCompletaReconstruyeElContextoConTodasLasRefs() {
        var id = OrdenId.nuevo();
        var externalId = ExternalId.de(UUID.randomUUID().toString());
        var datosNegocioId = DatosNegocioId.nuevo();
        var entidad = new ProcesoSagaPrincipalEntity(id.valor(), datosNegocioId.valor(),
                "ref1", "ref2", "ref3", "ref4", "ref5", "ref6", "ref7", "ref8");
        when(repo.findById(id.valor())).thenReturn(Optional.of(entidad));

        var rearmada = (SagaPrincipal) soporte.rearmar(id, externalId, "TERMINADA", List.of());

        assertThat(rearmada.estado()).isEqualTo(EstadoSagaPrincipal.TERMINADA);
        assertThat(rearmada.contexto().datosNegocioId()).isEqualTo(datosNegocioId);
        assertThat(rearmada.contexto().refPaso1().valor()).isEqualTo("ref1");
        assertThat(rearmada.contexto().refPaso8().valor()).isEqualTo("ref8");
    }

    @Test
    void rearmar_conEntidadSinNingunaRefReconstruyeElContextoConTodasLasRefsNulas() {
        var id = OrdenId.nuevo();
        var externalId = ExternalId.de(UUID.randomUUID().toString());
        var datosNegocioId = DatosNegocioId.nuevo();
        var entidad = new ProcesoSagaPrincipalEntity(id.valor(), datosNegocioId.valor(),
                null, null, null, null, null, null, null, null);
        when(repo.findById(id.valor())).thenReturn(Optional.of(entidad));

        var rearmada = (SagaPrincipal) soporte.rearmar(id, externalId, "INICIAL", List.of());

        assertThat(rearmada.estado()).isEqualTo(EstadoSagaPrincipal.INICIAL);
        assertThat(rearmada.contexto().refPaso1()).isNull();
        assertThat(rearmada.contexto().refPaso8()).isNull();
    }

    @Test
    void rearmar_sinFilaEnLaSateliteLanzaIllegalArgumentException() {
        var id = OrdenId.nuevo();
        when(repo.findById(id.valor())).thenReturn(Optional.empty());

        assertThatThrownBy(() -> soporte.rearmar(id, ExternalId.de(UUID.randomUUID().toString()), "INICIAL", List.of()))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void borrarContexto_delegaEnElBorradoPorLoteDelRepo() {
        var ids = List.of(UUID.randomUUID());

        soporte.borrarContexto(ids);

        verify(repo).borrarPorIds(ids);
    }
}
