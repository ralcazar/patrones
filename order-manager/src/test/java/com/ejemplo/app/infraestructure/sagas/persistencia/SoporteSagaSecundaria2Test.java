package com.ejemplo.app.infraestructure.sagas.persistencia;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import com.ejemplo.app.business.ordermanager.dominio.ExternalId;
import com.ejemplo.app.business.ordermanager.dominio.OrdenId;
import com.ejemplo.app.business.sagas.dominio.comun.ContextoArranque;
import com.ejemplo.app.business.sagas.dominio.comun.RefPaso5;
import com.ejemplo.app.business.sagas.dominio.sagasecundaria2.EstadoSagaSecundaria2;
import com.ejemplo.app.business.sagas.dominio.sagasecundaria2.RefRespuesta;
import com.ejemplo.app.business.sagas.dominio.sagasecundaria2.SagaSecundaria2;

/** {@link com.ejemplo.app.infraestructure.ordermanager.persistencia.MapeadorProceso} de la saga secundaria 2. */
class SoporteSagaSecundaria2Test {

    private final SoporteSagaSecundaria2 soporte = new SoporteSagaSecundaria2();

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
    void desarmarYRearmar_sinRefRespuestaHaceIdaYVuelta() {
        var id = OrdenId.nuevo();
        var externalId = ExternalId.de(UUID.randomUUID().toString());
        var saga = SagaSecundaria2.crear(id, new ContextoArranque.ArranqueSecundaria2(externalId, new RefPaso5("ref5")));

        var persistible = soporte.desarmar(saga);
        var rearmada = (SagaSecundaria2) soporte.rearmar(id, externalId, persistible.estado(),
                persistible.contexto(), List.of());

        assertThat(persistible.estado()).isEqualTo("INICIAL");
        assertThat(persistible.contexto()).containsEntry("refPaso5", "ref5").doesNotContainKey("refRespuesta");
        assertThat(rearmada.estado()).isEqualTo(EstadoSagaSecundaria2.INICIAL);
        assertThat(rearmada.refPaso5()).isEqualTo(saga.refPaso5());
        assertThat(rearmada.refRespuesta()).isNull();
    }

    @Test
    void desarmarYRearmar_conRefRespuestaHaceIdaYVuelta() {
        var id = OrdenId.nuevo();
        var externalId = ExternalId.de(UUID.randomUUID().toString());
        var saga = SagaSecundaria2.crear(id, new ContextoArranque.ArranqueSecundaria2(externalId, new RefPaso5("ref5")));
        saga.respuestaRecibida(new RefRespuesta("refRespuesta"));

        var persistible = soporte.desarmar(saga);
        var rearmada = (SagaSecundaria2) soporte.rearmar(id, externalId, persistible.estado(),
                persistible.contexto(), List.of());

        assertThat(persistible.estado()).isEqualTo("TERMINADA");
        assertThat(persistible.contexto()).containsEntry("refRespuesta", "refRespuesta");
        assertThat(rearmada.estado()).isEqualTo(EstadoSagaSecundaria2.TERMINADA);
        assertThat(rearmada.refRespuesta()).isEqualTo(saga.refRespuesta());
    }
}
