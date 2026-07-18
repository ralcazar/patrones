package com.ejemplo.app.infraestructure.sagas.persistencia;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import com.ejemplo.app.business.ordermanager.dominio.ExternalId;
import com.ejemplo.app.business.ordermanager.dominio.OrdenId;
import com.ejemplo.app.business.sagas.dominio.comun.ContextoArranque;
import com.ejemplo.app.business.sagas.dominio.comun.RefPaso7;
import com.ejemplo.app.business.sagas.dominio.sagasecundaria3.EstadoSagaSecundaria3;
import com.ejemplo.app.business.sagas.dominio.sagasecundaria3.RefEjecucion;
import com.ejemplo.app.business.sagas.dominio.sagasecundaria3.ResultadoPasoSecundaria3;
import com.ejemplo.app.business.sagas.dominio.sagasecundaria3.SagaSecundaria3;

/** {@link com.ejemplo.app.infraestructure.ordermanager.persistencia.MapeadorProceso} de la saga secundaria 3. */
class SoporteSagaSecundaria3Test {

    private final SoporteSagaSecundaria3 soporte = new SoporteSagaSecundaria3();

    @Test
    void tipo_esSecundaria3() {
        assertThat(soporte.tipo()).isEqualTo(SagaSecundaria3.TIPO);
    }

    @Test
    void pasoPendiente_soloEnInicialEsEjecucion() {
        assertThat(soporte.pasoPendiente("INICIAL")).isEqualTo("EJECUCION");
        assertThat(soporte.pasoPendiente("TERMINADA")).isNull();
    }

    @Test
    void datosManualesObligatorios_nuncaLoEs() {
        assertThat(soporte.datosManualesObligatorios("INICIAL")).isFalse();
        assertThat(soporte.datosManualesObligatorios("TERMINADA")).isFalse();
    }

    @Test
    void cancelable_nuncaLoEs() {
        assertThat(soporte.cancelable("INICIAL")).isFalse();
        assertThat(soporte.cancelable("TERMINADA")).isFalse();
    }

    @Test
    void desarmarYRearmar_sinRefEjecucionHaceIdaYVuelta() {
        var id = OrdenId.nuevo();
        var externalId = ExternalId.de(UUID.randomUUID().toString());
        var saga = SagaSecundaria3.crear(id, new ContextoArranque.ArranqueSecundaria3(externalId, new RefPaso7("ref7")));

        var persistible = soporte.desarmar(saga);
        var rearmada = (SagaSecundaria3) soporte.rearmar(id, externalId, persistible.estado(),
                persistible.contexto(), List.of());

        assertThat(persistible.estado()).isEqualTo("INICIAL");
        assertThat(persistible.contexto()).containsEntry("refPaso7", "ref7").doesNotContainKey("refEjecucion");
        assertThat(rearmada.estado()).isEqualTo(EstadoSagaSecundaria3.INICIAL);
        assertThat(rearmada.refPaso7()).isEqualTo(saga.refPaso7());
        assertThat(rearmada.refEjecucion()).isNull();
    }

    @Test
    void desarmarYRearmar_conRefEjecucionHaceIdaYVuelta() {
        var id = OrdenId.nuevo();
        var externalId = ExternalId.de(UUID.randomUUID().toString());
        var saga = SagaSecundaria3.crear(id, new ContextoArranque.ArranqueSecundaria3(externalId, new RefPaso7("ref7")));
        saga.aplicarYAvanzar(new ResultadoPasoSecundaria3.Ejecutada(new RefEjecucion("refEjecucion")));

        var persistible = soporte.desarmar(saga);
        var rearmada = (SagaSecundaria3) soporte.rearmar(id, externalId, persistible.estado(),
                persistible.contexto(), List.of());

        assertThat(persistible.estado()).isEqualTo("TERMINADA");
        assertThat(persistible.contexto()).containsEntry("refEjecucion", "refEjecucion");
        assertThat(rearmada.estado()).isEqualTo(EstadoSagaSecundaria3.TERMINADA);
        assertThat(rearmada.refEjecucion()).isEqualTo(saga.refEjecucion());
    }
}
