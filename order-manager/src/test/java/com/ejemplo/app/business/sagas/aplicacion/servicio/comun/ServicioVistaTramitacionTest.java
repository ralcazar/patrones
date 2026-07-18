package com.ejemplo.app.business.sagas.aplicacion.servicio.comun;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import com.ejemplo.app.business.ordermanager.aplicacion.puerto.entrada.CasoUsoConsultarOrdenesSoporte.OrdenDetalle;
import com.ejemplo.app.business.ordermanager.aplicacion.puerto.entrada.CasoUsoConsultarOrdenesSoporte.OrdenResumen;
import com.ejemplo.app.business.ordermanager.aplicacion.puerto.salida.PuertoConsultaOrdenesSoporte;
import com.ejemplo.app.business.ordermanager.dominio.ExternalId;
import com.ejemplo.app.business.ordermanager.dominio.OrdenId;
import com.ejemplo.app.business.sagas.dominio.sagaprincipal.SagaPrincipal;
import com.ejemplo.app.business.sagas.dominio.sagasecundaria1.SagaSecundaria1;
import com.ejemplo.app.business.sagas.dominio.sagasecundaria2.SagaSecundaria2;

/** Compone la vista de conjunto de una tramitación separando la principal de las secundarias. */
class ServicioVistaTramitacionTest {

    private static OrdenDetalle detalleDe(com.ejemplo.app.business.ordermanager.dominio.TipoOrden tipo,
            ExternalId externalId) {
        var resumen = new OrdenResumen(OrdenId.nuevo(), tipo, externalId, "INICIAL", 0,
                null, Instant.now(), Instant.now(), Instant.now());
        return new OrdenDetalle(resumen, true, List.of(), List.of());
    }

    @Test
    void vistaTramitacion_separaLaPrincipalDeLasSecundarias() {
        var puerto = mock(PuertoConsultaOrdenesSoporte.class);
        var externalId = ExternalId.de(UUID.randomUUID().toString());
        var principal = detalleDe(SagaPrincipal.TIPO, externalId);
        var secundaria1 = detalleDe(SagaSecundaria1.TIPO, externalId);
        var secundaria2 = detalleDe(SagaSecundaria2.TIPO, externalId);
        when(puerto.porExternalId(any())).thenReturn(List.of(secundaria1, principal, secundaria2));
        var servicio = new ServicioVistaTramitacion(puerto);

        var vista = servicio.vistaTramitacion(externalId);

        assertThat(vista.externalId()).isEqualTo(externalId);
        assertThat(vista.principal()).isSameAs(principal);
        assertThat(vista.secundarias()).containsExactlyInAnyOrder(secundaria1, secundaria2);
    }

    @Test
    void vistaTramitacion_sinNingunaOrdenTodaviaDejaLaPrincipalNula() {
        var puerto = mock(PuertoConsultaOrdenesSoporte.class);
        var externalId = ExternalId.de(UUID.randomUUID().toString());
        when(puerto.porExternalId(any())).thenReturn(List.of());
        var servicio = new ServicioVistaTramitacion(puerto);

        var vista = servicio.vistaTramitacion(externalId);

        assertThat(vista.principal()).isNull();
        assertThat(vista.secundarias()).isEmpty();
    }
}
