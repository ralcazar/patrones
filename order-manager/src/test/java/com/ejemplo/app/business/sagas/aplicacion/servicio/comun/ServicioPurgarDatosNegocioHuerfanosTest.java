package com.ejemplo.app.business.sagas.aplicacion.servicio.comun;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;

import org.junit.jupiter.api.Test;

import com.ejemplo.app.business.sagas.aplicacion.puerto.salida.RepositorioDatosNegocio;
import com.ejemplo.app.business.sagas.dominio.datosnegocio.DatosNegocioId;

class ServicioPurgarDatosNegocioHuerfanosTest {

    private final RepositorioDatosNegocio repoDatos = mock(RepositorioDatosNegocio.class);
    private final ServicioPurgarDatosNegocioHuerfanos servicio = new ServicioPurgarDatosNegocioHuerfanos(repoDatos);

    @Test
    void purgarHuerfanos_borraCadaHuerfanoDevueltoYDevuelveElRecuento() {
        var id1 = DatosNegocioId.nuevo();
        var id2 = DatosNegocioId.nuevo();
        when(repoDatos.idsHuerfanos()).thenReturn(List.of(id1, id2));

        var borrados = servicio.purgarHuerfanos();

        assertThat(borrados).isEqualTo(2);
        verify(repoDatos).borrar(id1);
        verify(repoDatos).borrar(id2);
    }

    @Test
    void purgarHuerfanos_sinHuerfanosNoBorraNadaYDevuelveCero() {
        when(repoDatos.idsHuerfanos()).thenReturn(List.of());

        var borrados = servicio.purgarHuerfanos();

        assertThat(borrados).isZero();
    }
}
