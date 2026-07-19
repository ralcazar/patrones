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
import com.ejemplo.app.business.sagas.dominio.comun.ContextoArranque;
import com.ejemplo.app.business.sagas.dominio.comun.RefPaso5;
import com.ejemplo.app.business.sagas.dominio.sagasecundaria2.EstadoSagaSecundaria2;
import com.ejemplo.app.business.sagas.dominio.sagasecundaria2.RefRespuesta;
import com.ejemplo.app.business.sagas.dominio.sagasecundaria2.SagaSecundaria2;

/**
 * {@link com.ejemplo.app.infraestructure.ordermanager.persistencia.MapeadorProceso} de la
 * saga secundaria 2. El repo JPA de la satélite se dobla con Mockito (test de
 * infraestructure, ver la nota en {@link SoporteSagaPrincipalTest}).
 */
class SoporteSagaSecundaria2Test {

    private final ProcesoSagaSecundaria2JpaRepository repo = mock(ProcesoSagaSecundaria2JpaRepository.class);
    private final SoporteSagaSecundaria2 soporte = new SoporteSagaSecundaria2(repo);

    @Test
    void tipo_esSecundaria2() {
        assertThat(soporte.tipo()).isEqualTo(SagaSecundaria2.TIPO);
    }

    @Test
    void pasoPendiente_enInicialOEsperandoRespuestaEsSolicitud() {
        assertThat(soporte.pasoPendiente("INICIAL")).isEqualTo("SOLICITUD");
        assertThat(soporte.pasoPendiente("ESPERANDO_RESPUESTA")).isEqualTo("SOLICITUD");
        assertThat(soporte.pasoPendiente("TERMINADA")).isNull();
    }

    @Test
    void datosManualesObligatorios_nuncaLoEs() {
        assertThat(soporte.datosManualesObligatorios("INICIAL")).isFalse();
        assertThat(soporte.datosManualesObligatorios("ESPERANDO_RESPUESTA")).isFalse();
    }

    @Test
    void cancelable_nuncaLoEs() {
        assertThat(soporte.cancelable("INICIAL")).isFalse();
        assertThat(soporte.cancelable("TERMINADA")).isFalse();
    }

    @Test
    void estado_devuelveElNombreDelEstadoDeLaFsm() {
        var saga = SagaSecundaria2.crear(OrdenId.nuevo(),
                new ContextoArranque.ArranqueSecundaria2(ExternalId.de(UUID.randomUUID().toString()), new RefPaso5("ref5")));

        assertThat(soporte.estado(saga)).isEqualTo("INICIAL");
    }

    @Test
    void guardarContexto_sinRefRespuestaGuardaLaEntidadConRefRespuestaANull() {
        var id = OrdenId.nuevo();
        var saga = SagaSecundaria2.crear(id,
                new ContextoArranque.ArranqueSecundaria2(ExternalId.de(UUID.randomUUID().toString()), new RefPaso5("ref5")));
        var captor = ArgumentCaptor.forClass(ProcesoSagaSecundaria2Entity.class);

        soporte.guardarContexto(saga);

        verify(repo).save(captor.capture());
        var entidad = captor.getValue();
        assertThat(entidad.getOrdenId()).isEqualTo(id.valor());
        assertThat(entidad.getRefPaso5()).isEqualTo("ref5");
        assertThat(entidad.getRefRespuesta()).isNull();
    }

    @Test
    void guardarContexto_conRefRespuestaGuardaLaEntidadCompleta() {
        var id = OrdenId.nuevo();
        var saga = SagaSecundaria2.crear(id,
                new ContextoArranque.ArranqueSecundaria2(ExternalId.de(UUID.randomUUID().toString()), new RefPaso5("ref5")));
        saga = saga.respuestaRecibida(new RefRespuesta("refRespuesta"));
        var captor = ArgumentCaptor.forClass(ProcesoSagaSecundaria2Entity.class);

        soporte.guardarContexto(saga);

        verify(repo).save(captor.capture());
        assertThat(captor.getValue().getRefRespuesta()).isEqualTo("refRespuesta");
        assertThat(soporte.estado(saga)).isEqualTo("TERMINADA");
    }

    @Test
    void rearmar_conEntidadSinRefRespuestaReconstruyeElContextoConNull() {
        var id = OrdenId.nuevo();
        var externalId = ExternalId.de(UUID.randomUUID().toString());
        var entidad = new ProcesoSagaSecundaria2Entity(id.valor(), "ref5", null);
        when(repo.findById(id.valor())).thenReturn(Optional.of(entidad));

        var rearmada = (SagaSecundaria2) soporte.rearmar(id, externalId, "INICIAL", List.of());

        assertThat(rearmada.estado()).isEqualTo(EstadoSagaSecundaria2.INICIAL);
        assertThat(rearmada.refPaso5().valor()).isEqualTo("ref5");
        assertThat(rearmada.refRespuesta()).isNull();
    }

    @Test
    void rearmar_conEntidadCompletaReconstruyeElContextoConLaRefRespuesta() {
        var id = OrdenId.nuevo();
        var externalId = ExternalId.de(UUID.randomUUID().toString());
        var entidad = new ProcesoSagaSecundaria2Entity(id.valor(), "ref5", "refRespuesta");
        when(repo.findById(id.valor())).thenReturn(Optional.of(entidad));

        var rearmada = (SagaSecundaria2) soporte.rearmar(id, externalId, "TERMINADA", List.of());

        assertThat(rearmada.estado()).isEqualTo(EstadoSagaSecundaria2.TERMINADA);
        assertThat(rearmada.refRespuesta().valor()).isEqualTo("refRespuesta");
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
